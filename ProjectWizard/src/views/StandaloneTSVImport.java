/*******************************************************************************
 * QBiC Project Wizard enables users to create hierarchical experiments including different study
 * conditions using factorial design. Copyright (C) "2016" Andreas Friedrich
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package views;

import java.io.File;
import java.util.List;
import java.util.Map;

import logging.Log4j2Logger;
import main.SampleSummaryBean;
import model.DesignType;
import model.ISampleBean;
import uicomponents.ExperimentSummaryTable;
import uicomponents.MissingInfoComponent;
import uicomponents.ProjectInformationComponent;
import uicomponents.Styles;
import uicomponents.Styles.NotificationType;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.FileResource;
import com.vaadin.server.VaadinService;
import com.vaadin.ui.Button;
import com.vaadin.ui.Component;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.Upload;
import com.vaadin.ui.VerticalLayout;

import control.Functions;

public class StandaloneTSVImport extends VerticalLayout implements IRegistrationView {

  /**
   * 
   */
  private static final long serialVersionUID = 5358966181721590658L;
  private Button register;
  private OptionGroup importOptions;
  private VerticalLayout infos;
  // private Label error;
  private Upload upload;
  private MissingInfoComponent questionaire;
  private ExperimentSummaryTable summary;
  private List<List<ISampleBean>> samples;
  private Label registerInfo;
  private ProgressBar bar;
  private Button downloadTSV;

  logging.Logger logger = new Log4j2Logger(StandaloneTSVImport.class);

  public StandaloneTSVImport() {
    setMargin(true);
    setSpacing(true);

    this.questionaire = new MissingInfoComponent();


    importOptions = new OptionGroup("Import Format");
    importOptions.addItems("QBiC", "Standard", "MHC Ligandomics (measured)");// , "MHC Ligandomics
                                                                             // (preparation)");

    importOptions.addValueChangeListener(new ValueChangeListener() {
      @Override
      public void valueChange(ValueChangeEvent event) {
        upload.setEnabled(importOptions.getValue() != null);
      }
    });
    infos = new VerticalLayout();
    infos.setCaption("Format Information");

    infos.addComponent(Styles.getPopupViewContaining(createTSVDownloadComponent(DesignType.QBIC,
        "QBiC openBIS import format. Not recommended for external users.")));
    infos.addComponent(Styles.getPopupViewContaining(createTSVDownloadComponent(DesignType.Standard,
        "The Standard Import format for experimental designs containing information about organism, tissues/cell cultures and the analyte preparations.")));
    infos.addComponent(Styles.getPopupViewContaining(createTSVDownloadComponent(
        DesignType.MHC_Ligands_Finished,
        "Import format for tables of mass spectrometry measurements of MHC Ligands (Immunology group).")));
  }

  public void initView(Upload upload) {
    HorizontalLayout optionsInfo = new HorizontalLayout();
    optionsInfo.addComponent(importOptions);
    optionsInfo.addComponent(infos);

    // design type selection and info
    addComponent(optionsInfo);

    // file upload component
    this.upload = upload;
    addComponent(this.upload);
    upload.setEnabled(false);

    // missing info input layout
    addComponent(questionaire);

    // summary of imortet samples
    summary = new ExperimentSummaryTable();
    summary.setVisible(false);
    addComponent(summary);

    // sample registration button
    register = new Button("Register All");
    register.setVisible(false);
    addComponent(register);

    // registration progress information
    registerInfo = new Label();
    bar = new ProgressBar();
    registerInfo.setVisible(false);
    bar.setVisible(false);
    addComponent(registerInfo);
    addComponent(bar);
  }

  private Component createTSVDownloadComponent(DesignType type, String info) {
    VerticalLayout v = new VerticalLayout();
    v.setSpacing(true);
    Label l = new Label(info);
    l.setWidth("300px");
    v.addComponent(l);
    Button button = new Button("Download Example");
    v.addComponent(button);

    String basepath = VaadinService.getCurrent().getBaseDirectory().getAbsolutePath();
    FileDownloader tsvDL = new FileDownloader(
        new FileResource(new File(basepath + "/WEB-INF/files/" + type.getFileName())));
    tsvDL.extend(button);

    return v;
  }

  public Button getRegisterButton() {
    return this.register;
  }

  public void setSummary(List<SampleSummaryBean> beans) {
    summary.setSamples(beans);
    summary.setVisible(true);
  }

  public void setProcessed(List<List<ISampleBean>> processed) {
    samples = processed;
  }

  public void setRegEnabled(boolean b) {
    register.setEnabled(b);
    register.setVisible(b);
  }

  public List<List<ISampleBean>> getSamples() {
    return samples;
  }


  public void registrationDone(boolean sqlDown, String errors) {
    // TODO when adding mysql metadata handle sql down
    if (errors.isEmpty()) {
      logger.info("Sample registration complete!");
      Styles.notification("Registration complete!", "Registration of samples complete.",
          NotificationType.SUCCESS);
      register.setEnabled(false);
      switch (getSelectedDesignOption()) {
        case Standard:
        case MHC_Ligands_Finished:
          downloadTSV.setEnabled(true);
          break;
        default:
          break;
      }
    } else {
      String feedback = "Sample registration could not be completed. Reason: " + errors;
      logger.error(feedback);
      Styles.notification("Registration failed!", feedback, NotificationType.ERROR);
    }
  }

  public ProgressBar getProgressBar() {
    return bar;
  }

  public Label getProgressLabel() {
    return registerInfo;
  }

  public boolean summaryIsSet() {
    return (summary.size() > 0);
  }

  public void resetAfterUpload() {
    summary.removeAllItems();
    summary.setVisible(false);
    registerInfo.setVisible(false);
    bar.setVisible(false);
    if (downloadTSV != null)
      removeComponent(downloadTSV);
  }

  public DesignType getSelectedDesignOption() {
    if (importOptions.getValue() != null) {
      switch (importOptions.getValue().toString()) {
        case "QBiC":
          return DesignType.QBIC;
        case "Standard":
          return DesignType.Standard;
        case "MHC Ligandomics (preparation)":
          return DesignType.MHC_Ligands_Plan;
        case "MHC Ligandomics (measured)":
          return DesignType.MHC_Ligands_Finished;
        default:
          return DesignType.Standard;
      }
    } else
      return null;
  }

  public MissingInfoComponent initMissingInfoComponent(
      ProjectInformationComponent projectInfoComponent,
      Map<String, List<String>> missingCategoryToValues, Map<String, List<String>> catToVocabulary,
      ValueChangeListener missingInfoFilledListener) {
    MissingInfoComponent newQ = new MissingInfoComponent();
    newQ.init(projectInfoComponent, missingCategoryToValues, catToVocabulary,
        missingInfoFilledListener);
    replaceComponent(questionaire, newQ);
    questionaire = newQ;
    return questionaire;
  }

  public MissingInfoComponent getMissingInfoComponent() {
    return questionaire;
  }

  public void setTSVWithBarcodes(String tsvContent, String name) {
    if (downloadTSV != null)
      removeComponent(downloadTSV);
    downloadTSV = new Button("Download Barcodes");
    addComponent(downloadTSV);
    FileDownloader tsvDL = new FileDownloader(Functions.getFileStream(tsvContent, name, "tsv"));
    tsvDL.extend(downloadTSV);
  }

  public void showRegistration() {
    bar.setVisible(true);
    registerInfo.setVisible(true);
  }

}
