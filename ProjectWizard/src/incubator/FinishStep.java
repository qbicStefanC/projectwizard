/*******************************************************************************
 * QBiC Project Wizard enables users to create hierarchical experiments including different study conditions using factorial design.
 * Copyright (C) "2016"  Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package incubator;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import logging.Log4j2Logger;
import main.OpenBisClient;
import main.ProjectwizardUI;
import model.AttachmentConfig;
import model.AttachmentInformation;
import model.ISampleBean;

import org.vaadin.teemu.wizards.Wizard;
import org.vaadin.teemu.wizards.WizardStep;

import parser.XMLParser;
import processes.AttachmentMover;
import processes.MoveUploadsReadyRunnable;
import processes.TSVReadyRunnable;
import properties.Factor;
import uicomponents.UploadsPanel;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;
import ch.systemsx.cisd.openbis.plugin.query.shared.api.v1.dto.QueryTableModel;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.StreamResource;
import com.vaadin.shared.ui.label.ContentMode;
import com.vaadin.ui.Button;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.Table;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.themes.ValoTheme;

import concurrency.UpdateProgressBar;

import de.uni_tuebingen.qbic.main.LiferayAndVaadinUtils;

/**
 * Wizard Step to downloadTSV and upload the TSV file to and from and register samples and context
 * 
 * @author Andreas Friedrich
 * 
 */
public class FinishStep implements WizardStep {

  private VerticalLayout main;
  private Label summary;
  private VerticalLayout downloads;
  private ProgressBar bar;
  private Label info;
  private Button dlEntities;
  private Button dlExtracts;
  private Button dlPreps;
  // private Table summary;
  private CheckBox attach;
  private UploadsPanel uploads;
  private Wizard w;
  private AttachmentConfig attachConfig;

  private logging.Logger logger = new Log4j2Logger(FinishStep.class);
  private List<FileDownloader> downloaders = new ArrayList<FileDownloader>();

  public FinishStep(final Wizard w, AttachmentConfig attachmentConfig) {
    this.w = w;
    this.attachConfig = attachmentConfig;

    main = new VerticalLayout();
    main.setMargin(true);
    main.setSpacing(true);
    Label header = new Label("Summary and File Upload");
    main.addComponent(ProjectwizardUI
        .questionize(
            header,
            "Here you can download spreadsheets of the samples in your experiment "
                + "and upload informative files belonging to your project, e.g. treatment information. "
                + "It might take a few seconds for your files to show up in our project browser.",
            "Last Step"));
    summary = new Label();
    summary.setContentMode(ContentMode.PREFORMATTED);
    summary.setWidth("300px");
    main.addComponent(summary);

    downloads = new VerticalLayout();
    downloads.setCaption("Download Spreadsheets:");
    downloads.setSpacing(true);
    dlEntities = new Button("Sample Sources");
    dlExtracts = new Button("Sample Extracts");
    dlPreps = new Button("Sample Preparations");
    dlEntities.setEnabled(false);
    dlExtracts.setEnabled(false);
    dlPreps.setEnabled(false);
    downloads.addComponent(dlEntities);
    downloads.addComponent(dlExtracts);
    downloads.addComponent(dlPreps);

    this.bar = new ProgressBar();
    this.info = new Label();
    info.setCaption("Preparing Spreadsheets");
    main.addComponent(bar);
    main.addComponent(info);
    main.addComponent(downloads);
    attach = new CheckBox("Upload Additional Files");
    attach.setVisible(false);
    attach.addValueChangeListener(new ValueChangeListener() {

      @Override
      public void valueChange(ValueChangeEvent event) {
        uploads.setVisible(attach.getValue());
        w.getFinishButton().setVisible(!attach.getValue());
      }
    });
    main.addComponent(attach);
  }

  public void fileCommitDone() {
    uploads.commitDone();
    logger.info("Moving of files to Datamover folder complete!");
    Notification n =
        new Notification(
            "Registration of files complete. It might take a few minutes for your files to show up in the navigator. \n"
                + "You can end the project creation by clicking 'Finish'.");
    n.setStyleName(ValoTheme.NOTIFICATION_CLOSABLE);
    n.setDelayMsec(-1);
    n.show(UI.getCurrent().getPage());
    w.getFinishButton().setVisible(true);
  }

  public void setExperimentInfos(String space, String proj, String desc,
      Map<String, List<Sample>> samplesByExperiment, OpenBisClient openbis) {
    int entitieNum = 0;
    int samplesNum = 0;
    List<String> ids = new ArrayList<String>();
    // int i = 0;
    for (String exp : samplesByExperiment.keySet()) {
      // i++;
      List<Sample> samps = samplesByExperiment.get(exp);
      for (Sample s : samps)
        ids.add(s.getIdentifier());
      int amount = samps.size();
      // String type = "Unknown";
      String sampleType = samps.get(0).getSampleTypeCode();
      switch (sampleType) {
        case "Q_BIOLOGICAL_ENTITY":
          entitieNum += amount;
          // type = "Sample Sources";
          break;
        case "Q_BIOLOGICAL_SAMPLE":
          samplesNum += amount;
          // type = "Sample Extracts";
          break;
        case "Q_TEST_SAMPLE":
          samplesNum += amount;
          // type = "Sample Preparations";
          break;
        default:
          break;
      }
      // summary.addItem(new Object[] {type, amount}, i);
    }
    // summary.setPageLength(summary.size());
    summary.setValue("Your Experimental Design was registered. Project " + proj + " now has "
        + entitieNum + " Sample Sources and " + samplesNum + " samples. \n"
        + "Project description: " + desc.substring(0, Math.min(desc.length(), 60)) + "...");
    w.getFinishButton().setVisible(true);

    initUpload(space, proj, openbis);
    prepareSpreadsheets(ids, space, proj, openbis);
  }

  private void prepareSpreadsheets(final List<String> ids, final String space,
      final String project, final OpenBisClient openbis) {

    final FinishStep layout = this;
    bar.setVisible(true);
    info.setVisible(true);

    final int todo = 1;
    Thread t = new Thread(new Runnable() {
      volatile int current = 0;

      @Override
      public void run() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put(
            "types",
            new ArrayList<String>(Arrays.asList("Q_BIOLOGICAL_ENTITY", "Q_BIOLOGICAL_SAMPLE",
                "Q_TEST_SAMPLE")));
        params.put("ids", ids);
        updateProgressBar(current, todo, bar, info);

        while (openbis.getSamplesOfProject("/" + space + "/" + project).size() < ids.size()) {
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
        QueryTableModel table =
            openbis.getAggregationService("get-experimental-design-tsv", params);
        current++;
        updateProgressBar(current, todo, bar, info);

        UI.getCurrent().setPollInterval(-1);
//        UI.getCurrent().access(new TSVReadyRunnable(layout, table, project));TODO
      }
    });
    t.start();
    UI.getCurrent().setPollInterval(100);
  }

  private void updateProgressBar(int current, int todo, ProgressBar bar, Label info) {
    double frac = current * 1.0 / todo;
    UI.getCurrent().access(new UpdateProgressBar(bar, info, frac));
  }

  public void armButtons(List<StreamResource> streams) {
    armDownloadButton(dlEntities, streams.get(0), 1);
    armDownloadButton(dlExtracts, streams.get(1), 2);
    if (streams.size() > 2)
      armDownloadButton(dlPreps, streams.get(2), 3);
  }

  protected void armDownloadButton(Button b, StreamResource stream, int dlnum) {
    if (downloaders.size() < dlnum) {
      FileDownloader dl = new FileDownloader(stream);
      dl.extend(b);
      downloaders.add(dl);
    } else
      downloaders.get(dlnum - 1).setFileDownloadResource(stream);
    b.setEnabled(true);
  }

  private void initUpload(String space, String project, OpenBisClient openbis) {
    if (uploads != null)
      main.removeComponent(uploads);
    String userID = "admin";
    if (LiferayAndVaadinUtils.isLiferayPortlet())
      try {
        userID = LiferayAndVaadinUtils.getUser().getScreenName();
      } catch (Exception e) {
        logger.error(e.getMessage());
        logger.error("Could not contact Liferay for User screen name.");
      }

    this.uploads =
        new UploadsPanel(ProjectwizardUI.tmpFolder, space, project, new ArrayList<String>(
            Arrays.asList("Experimental Design")), userID, attachConfig, openbis);
    main.addComponent(uploads);
  }

  @Override
  public String getCaption() {
    return "Summary";
  }

  @Override
  public Component getContent() {
    return main;
  }

  @Override
  public boolean onAdvance() {
    return true;
  }

  @Override
  public boolean onBack() {
    return true;
  }

  public void enableDownloads(boolean b) {
    downloads.setEnabled(b);
  }

}
