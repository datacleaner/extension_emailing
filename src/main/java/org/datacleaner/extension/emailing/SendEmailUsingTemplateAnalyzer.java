package org.datacleaner.extension.emailing;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.metamodel.util.FileHelper;
import org.apache.metamodel.util.Func;
import org.apache.metamodel.util.Resource;
import org.datacleaner.api.Analyzer;
import org.datacleaner.api.Categorized;
import org.datacleaner.api.ComponentContext;
import org.datacleaner.api.Concurrent;
import org.datacleaner.api.Configured;
import org.datacleaner.api.Description;
import org.datacleaner.api.ExecutionLogMessage;
import org.datacleaner.api.Initialize;
import org.datacleaner.api.InputColumn;
import org.datacleaner.api.InputRow;
import org.datacleaner.api.MappedProperty;
import org.datacleaner.api.Provided;
import org.datacleaner.api.StringProperty;
import org.datacleaner.api.Validate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

@Named("Send email (using template)")
@Description("Sends emails using a template file in which values can be dynamically merged into the message.")
@Categorized(EmailingCategory.class)
@Concurrent(true)
public class SendEmailUsingTemplateAnalyzer implements Analyzer<SendEmailAnalyzerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailUsingTemplateAnalyzer.class);

    private static final String PROPERTY_TEMPLATE_VALUE_COLUMNS = "Template value columns";
    private static final String PROPERTY_TEMPLATE_VALUE_KEYS = "Template values";

    @Configured(value = "To (email address)", order = 100)
    InputColumn<String> emailAddressColumn;

    @Configured(value = "From (email address)", order = 110)
    String from = "Your name <your@email.com>";

    @Configured(value = "Email subject", order = 111)
    String subject;

    @Configured(value = "SMTP host", order = 501)
    String smtpHost = "smtp.gmail.com";

    @Configured(value = "SMTP port", order = 502)
    int smtpPort = 587;

    @Configured(order = 503)
    boolean tls = true;

    @Configured(order = 504)
    boolean ssl = false;

    @Configured(value = "SMTP username", order = 511)
    String smtpUsername;

    @Configured(value = "SMTP password", order = 512)
    @StringProperty(password = true)
    String smtpPassword;

    @Configured(order = 520)
    @Description("Sleep/wait time in milliseconds between every sent email. Negative value will allow concurrent sending, 0 will mean sequential sending with no delay.")
    long sleepTimeInMillis = -1;

    @Configured(value = "HTML email template", required = false)
    Resource htmlTemplate;

    @Configured(value = "Plain text email template", required = false)
    Resource plainTextTemplate;

    @Configured(required = false, value = PROPERTY_TEMPLATE_VALUE_COLUMNS)
    InputColumn<?>[] templateValues;

    @Configured(required = false, value = PROPERTY_TEMPLATE_VALUE_KEYS)
    @MappedProperty(PROPERTY_TEMPLATE_VALUE_COLUMNS)
    @Description("Key and values that will be used for search/replace while processing the templates and subject")
    String[] templateKeys;

    @Configured
    String templateEncoding = "UTF-8";

    @Inject
    @Provided
    ComponentContext _componentContext;

    private String _htmlTemplateString;
    private String _plainTextTemplateString;
    private EmailDispatcher _emailDispatcher;
    private AtomicInteger _successCount;
    private AtomicInteger _skipCount;
    private Collection<EmailResult> _failures;

    @Validate
    public void validate() {
        if (htmlTemplate == null && plainTextTemplate == null) {
            throw new IllegalStateException("At least one template needs to be provided");
        }
    }

    @Initialize
    public void init() {
        _htmlTemplateString = loadTemplate(htmlTemplate);
        _plainTextTemplateString = loadTemplate(plainTextTemplate);
        _emailDispatcher = new EmailDispatcher(smtpHost, smtpPort, smtpUsername, smtpPassword, from, tls, ssl);
        _successCount = new AtomicInteger();
        _skipCount = new AtomicInteger();
        _failures = new ConcurrentLinkedQueue<EmailResult>();
    }

    private String loadTemplate(Resource res) {
        if (res == null) {
            return null;
        }
        return res.read(new Func<InputStream, String>() {
            @Override
            public String eval(InputStream is) {
                return FileHelper.readInputStreamAsString(is, templateEncoding);
            }
        });
    }

    @Override
    public void run(InputRow row, int distinctCount) {
        final String emailAddressValue = row.getValue(emailAddressColumn);

        if (Strings.isNullOrEmpty(emailAddressValue) || emailAddressValue.indexOf('@') == -1) {
            logger.info("Skipping invalid email: {}", emailAddressValue);
            _skipCount.incrementAndGet();
            return;
        }

        final List<Object> values = row.getValues(templateValues);

        final String plainTextBody = buildBodyFromTemplate(_plainTextTemplateString, templateKeys, values);
        final String htmlBody = buildBodyFromTemplate(_htmlTemplateString, templateKeys, values);

        // also apply template keys to subject
        final String preparedSubject = buildBodyFromTemplate(subject, templateKeys, values);

        final EmailResult result = _emailDispatcher.sendMail(emailAddressValue, preparedSubject, templateEncoding,
                plainTextBody, htmlBody, sleepTimeInMillis);
        if (result.isSuccessful()) {
            _successCount.incrementAndGet();
        } else {
            _failures.add(result);

            // report to the execution log
            final Exception error = result.getError();
            _componentContext.publishMessage(new ExecutionLogMessage("Sending of email to '" + result.getRecipient()
                    + " failed! " + (error == null ? "" : error.getMessage())));
        }
    }

    private String buildBodyFromTemplate(String template, String[] keys, List<Object> values) {
        if (template == null || keys == null || values == null) {
            return template;
        }
        for (int i = 0; i < keys.length; i++) {
            String key = keys[i];
            Object value = values.get(i);
            template = replaceAll(template, key, value);
        }
        return template;
    }

    @Override
    public SendEmailAnalyzerResult getResult() {
        return new SendEmailAnalyzerResult(_successCount.get(), _skipCount.get(), _failures);
    }

    /**
     * Does a plain text replace without any regexes or magic
     * 
     * @param template
     * @param key
     * @param value
     * @return
     */
    private String replaceAll(String template, String key, Object value) {
        if (Strings.isNullOrEmpty(key.trim())) {
            return template;
        }

        if (value == null) {
            value = "<null>";
        }

        int fromIndex = 0;
        while (true) {
            final int index = template.indexOf(key, fromIndex);
            if (index == -1) {
                break;
            }
            final String valueStr = value.toString();
            template = template.replace(key, valueStr);
            fromIndex = index + valueStr.length();
        }

        return template;
    }
}
