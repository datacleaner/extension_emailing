package org.eobjects.datacleaner.extension.emailing;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.eobjects.analyzer.beans.api.Analyzer;
import org.eobjects.analyzer.beans.api.AnalyzerBean;
import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.beans.api.Description;
import org.eobjects.analyzer.beans.api.Initialize;
import org.eobjects.analyzer.beans.api.StringProperty;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

@AnalyzerBean("Send email (using record values)")
@Description("Sends emails using the record values as the email contents.")
@Categorized(EmailingCategory.class)
public class SendEmailUsingValueAnalyzer implements Analyzer<SendEmailAnalyzerResult> {

    private static final Logger logger = LoggerFactory.getLogger(SendEmailUsingValueAnalyzer.class);

    @Configured(value = "To (email address)", order = 100)
    InputColumn<String> emailAddressColumn;

    @Configured(value = "From (email address)", order = 110)
    String from = "Your name <your@email.com>";

    @Configured(value = "Email subject", order = 111)
    InputColumn<String> subject;

    @Configured(value = "Email body", order = 112)
    InputColumn<String> body;

    @Configured(value = "HTML email", order = 113)
    boolean htmlEmail = false;

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

    private EmailDispatcher _emailDispatcher;
    private AtomicInteger _successCount;
    private AtomicInteger _skipCount;
    private Collection<EmailResult> _failures;

    @Initialize
    public void initialize() {
        _emailDispatcher = new EmailDispatcher(smtpHost, smtpPort, smtpUsername, smtpPassword, from, tls, ssl);
        _successCount = new AtomicInteger();
        _skipCount = new AtomicInteger();
        _failures = new ConcurrentLinkedQueue<EmailResult>();
    }

    @Override
    public void run(InputRow row, int distinctCount) {
        final String subjectString = row.getValue(subject);
        final String emailAddressValue = row.getValue(emailAddressColumn);
        final String bodyString = row.getValue(body);

        if (Strings.isNullOrEmpty(emailAddressValue) || emailAddressValue.indexOf('@') == -1) {
            logger.info("Skipping invalid email: {}", emailAddressValue);
            _skipCount.incrementAndGet();
            return;
        }

        final EmailResult result = _emailDispatcher.sendMail(emailAddressValue, subjectString, "UTF-8", bodyString,
                null, sleepTimeInMillis);
        if (result.isSuccessful()) {
            _successCount.incrementAndGet();
        } else {
            _failures.add(result);
        }
    }

    @Override
    public SendEmailAnalyzerResult getResult() {
        return new SendEmailAnalyzerResult(_successCount.get(), _skipCount.get(), _failures);
    }

}
