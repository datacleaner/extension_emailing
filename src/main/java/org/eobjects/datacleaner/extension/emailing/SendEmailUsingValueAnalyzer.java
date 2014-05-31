package org.eobjects.datacleaner.extension.emailing;

import org.eobjects.analyzer.beans.api.Analyzer;
import org.eobjects.analyzer.beans.api.AnalyzerBean;
import org.eobjects.analyzer.beans.api.Categorized;
import org.eobjects.analyzer.beans.api.Configured;
import org.eobjects.analyzer.data.InputColumn;
import org.eobjects.analyzer.data.InputRow;

@AnalyzerBean("Send email (using a record value)")
@Categorized(EmailingCategory.class)
public class SendEmailUsingValueAnalyzer implements Analyzer<SendEmailAnalyzerResult> {

    @Configured
    InputColumn<String> emailAddress;

    @Override
    public void run(InputRow row, int distinctCount) {
        // TODO Auto-generated method stub
    }

    @Override
    public SendEmailAnalyzerResult getResult() {
        return new SendEmailAnalyzerResult();
    }

}
