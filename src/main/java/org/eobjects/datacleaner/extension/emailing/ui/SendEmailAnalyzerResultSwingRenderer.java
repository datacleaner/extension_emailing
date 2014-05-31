package org.eobjects.datacleaner.extension.emailing.ui;

import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JLabel;

import org.eobjects.analyzer.beans.api.RendererBean;
import org.eobjects.analyzer.result.renderer.AbstractRenderer;
import org.eobjects.analyzer.result.renderer.SwingRenderingFormat;
import org.eobjects.datacleaner.extension.emailing.EmailResult;
import org.eobjects.datacleaner.extension.emailing.SendEmailAnalyzerResult;
import org.eobjects.datacleaner.panels.DCPanel;
import org.eobjects.datacleaner.util.WidgetUtils;
import org.eobjects.datacleaner.widgets.Alignment;
import org.jdesktop.swingx.VerticalLayout;

@RendererBean(SwingRenderingFormat.class)
public class SendEmailAnalyzerResultSwingRenderer extends AbstractRenderer<SendEmailAnalyzerResult, JComponent> {

    @Override
    public JComponent render(SendEmailAnalyzerResult analyzerResult) {
        final int successCount = analyzerResult.getSuccessCount();
        final int skipCount = analyzerResult.getSkipCount();
        final int failureCount = analyzerResult.getFailureCount();

        final DCPanel panel = new DCPanel();
        panel.setLayout(new VerticalLayout(4));

        panel.add(createMetricText("Emails sent: ", successCount));
        panel.add(createMetricText("Emails skipped: ", skipCount));
        panel.add(createMetricText("Emails failed: ", failureCount));

        final Collection<EmailResult> failures = analyzerResult.getFailures();
        int failuresPresented = 0;
        for (EmailResult failureEmailResult : failures) {
            panel.add(createFailureText(failureEmailResult));
            failuresPresented++;
            if (failuresPresented > 20) {
                // prevent too many errors on page
                break;
            }
        }

        return panel;
    }

    private Component createFailureText(EmailResult failureEmailResult) {
        final JLabel recipientLabel = new JLabel(failureEmailResult.getRecipient());
        recipientLabel.setFont(WidgetUtils.FONT_NORMAL.deriveFont(Font.BOLD));

        DCPanel panel = new DCPanel(WidgetUtils.BG_COLOR_BRIGHTEST, WidgetUtils.BG_COLOR_BRIGHTEST);
        panel.setLayout(new FlowLayout(Alignment.LEFT.getFlowLayoutAlignment()));
        panel.add(new JLabel("Failed to send email to: "));
        panel.add(recipientLabel);

        final Exception error = failureEmailResult.getError();
        if (error != null) {
            String message = error.getMessage();
            panel.add(new JLabel("Error message (see log for details): " + message));
        }

        return WidgetUtils.decorateWithShadow(panel, false, 2);
    }

    private Component createMetricText(String metric, int value) {
        final JLabel valueLabel = new JLabel("" + value);
        valueLabel.setFont(WidgetUtils.FONT_NORMAL.deriveFont(Font.BOLD));

        DCPanel panel = new DCPanel(WidgetUtils.BG_COLOR_BRIGHTEST, WidgetUtils.BG_COLOR_BRIGHTEST);
        panel.setLayout(new FlowLayout(Alignment.LEFT.getFlowLayoutAlignment()));
        panel.add(new JLabel(metric));
        panel.add(valueLabel);
        return WidgetUtils.decorateWithShadow(panel, false, 2);
    }

}
