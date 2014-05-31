package org.eobjects.datacleaner.extension.emailing;

import java.io.InputStream;
import java.util.List;

import org.eobjects.analyzer.beans.api.Analyzer;
import org.eobjects.analyzer.beans.api.AnalyzerBean;
import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Concurrent;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.MappedProperty;
import org.eobjects.analyzer.beans.api.StringProperty;
import org.eobjects.analyzer.beans.api.Validate;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.eobjects.metamodel.util.FileHelper;
import org.eobjects.metamodel.util.Func;
import org.eobjects.metamodel.util.Resource;

import com.google.common.base.Strings;

@AnalyzerBean("Send email (using template)")
@Description("Sends emails using a template file in which values can be dynamically merged into the message.")
@Categorized(EmailingCategory.class)
@Concurrent(true)
public class SendEmailUsingTemplateAnalyzer implements Analyzer<SendEmailAnalyzerResult> {

    private static final String PROPERTY_TEMPLATE_VALUE_COLUMNS = "Template value columns";
    private static final String PROPERTY_TEMPLATE_VALUE_KEYS = "Template values";

    @Configured(value = "To (email address)", order = 100)
    InputColumn<String> emailAddressColumn;

    @Configured(value = "From (email address)", order = 110)
    String from = "Your name <your@email.com>";

    @Configured(value="Email subject", order = 111)
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

    private String htmlTemplateString;
    private String plainTextTemplateString;

    @Validate
    public void validate() {
        if (htmlTemplate == null && plainTextTemplate == null) {
            throw new IllegalStateException("At least one template needs to be provided");
        }
    }

    @Initialize
    public void init() {
        htmlTemplateString = loadTemplate(htmlTemplate);
        plainTextTemplateString = loadTemplate(plainTextTemplate);
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
        String emailAddressValue = row.getValue(emailAddressColumn);

        List<Object> values = row.getValues(templateValues);

        String plainTextBody = buildBodyFromTemplate(plainTextTemplateString, templateKeys, values);
        String htmlBody = buildBodyFromTemplate(htmlTemplateString, templateKeys, values);

        EmailDispatcher dispatcher = new EmailDispatcher(smtpHost, smtpPort, smtpUsername, smtpPassword, from, tls, ssl);
        
        // also apply template keys to subject
        String preparedSubject = buildBodyFromTemplate(subject, templateKeys, values);

        dispatcher.sendMail(emailAddressValue, preparedSubject, templateEncoding, plainTextBody, htmlBody);
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
        return new SendEmailAnalyzerResult();
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
