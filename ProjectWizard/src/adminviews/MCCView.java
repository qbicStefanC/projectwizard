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
package adminviews;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.commons.lang.StringUtils;

import parser.XMLParser;
import processes.RegisteredSamplesReadyRunnable;
import properties.Property;
import registration.OpenbisCreationController;
import uicomponents.Styles;
import views.IRegistrationView;

import logging.Log4j2Logger;
import model.ISampleBean;
import model.MCCPatient;
import model.OpenbisExperiment;
import model.TSVSampleBean;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Project;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.util.BeanItemContainer;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Label;
import com.vaadin.ui.ProgressBar;
import com.vaadin.ui.TabSheet;
import com.vaadin.ui.Table;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.themes.ValoTheme;
import componentwrappers.StandardTextField;
import uicomponents.Styles.*;
import control.IRegistrationController;
import control.SampleCounter;
import life.qbic.openbis.openbisclient.IOpenBisClient;

public class MCCView extends VerticalLayout implements IRegistrationView, IRegistrationController {
  /**
   * 
   */
  private static final long serialVersionUID = 5542816061866018937L;

  logging.Logger logger = new Log4j2Logger(MCCView.class);

  private IOpenBisClient openbis;
  private OpenbisCreationController creator;
  private XMLParser p = new XMLParser();
  private String user;
  // view
  private String mccSpace = "MULTISCALEHCC";
  private ComboBox mccProjects;
  private StandardTextField newProject;
  private StandardTextField treatment;
  private StandardTextField timepoint;
  private StandardTextField patient;
  private Table existingPatients;

  private TabSheet editView;
  private Table samples;
  private Table metaData;

  private ProgressBar bar;
  private Label registerInfo;
  private Button addSamples;

  // model
  private List<Sample> entities;
  private List<String> patients;
  private Set<String> cases;
  private SampleCounter counter;

  public MCCView(IOpenBisClient openbis, OpenbisCreationController creationController,
      String user) {
    this.openbis = openbis;
    this.creator = creationController;
    this.user = user;

    this.cases = new HashSet<String>();
    this.patients = new ArrayList<String>();

    mccProjects = new ComboBox("Source Project");
    List<String> projects = new ArrayList<String>();
    for (Project p : openbis.getProjectsOfSpace(mccSpace))
      projects.add(p.getCode());
    mccProjects.addStyleName(Styles.boxTheme);
    mccProjects.addItems(projects);
    mccProjects.setImmediate(true);

    newProject = new StandardTextField("New Project");
    newProject.setImmediate(true);
    newProject.setWidth("80px");

    HorizontalLayout projectTab = new HorizontalLayout();
    projectTab.setSpacing(true);
    projectTab.addComponent(mccProjects);
    projectTab.addComponent(newProject);

    treatment = new StandardTextField("Treatment");
    timepoint = new StandardTextField("Timepoint");
    timepoint.setWidth("40px");
    patient = new StandardTextField("Patient #");
    patient.setWidth("50px");

    HorizontalLayout paramTab = new HorizontalLayout();
    paramTab.setSpacing(true);
    paramTab.addComponent(treatment);
    paramTab.addComponent(patient);
    paramTab.addComponent(timepoint);

    existingPatients = new Table("Existing Patients");
    existingPatients.setStyleName(Styles.tableTheme);
    existingPatients.setPageLength(1);

    editView = new TabSheet();
    editView.addStyleName(ValoTheme.TABSHEET_FRAMED);

    samples = new Table("Samples");
    samples.setStyleName(Styles.tableTheme);
    samples.setPageLength(1);

    metaData = new Table();
    metaData.setEditable(true);
    metaData.setStyleName(Styles.tableTheme);

    editView.addTab(samples, "Overview");
    editView.addTab(metaData, "Change Metadata");
    editView.setVisible(false);

    registerInfo = new Label();
    bar = new ProgressBar();
    addSamples = new Button("Add Samples");
    addSamples.setEnabled(false);
    initMCCListeners();
    addComponent(Styles.questionize(projectTab,
        "Samples can only be added if Timepoint, Treatment, Project and Patient Number "
            + "are filled in and they don't already exist in the current project. E.g. you can add a new timepoint for the same patient and "
            + "treatment but not the same timepoint.",
        "Adding new Samples"));
    addComponent(paramTab);
    addComponent(existingPatients);
    addComponent(editView);
    addComponent(registerInfo);
    addComponent(bar);
    addComponent(addSamples);
  }

  private boolean allValid() {
    boolean project = !newProject.isEmpty() || mccProjects.getValue() != null;
    boolean treat = !treatment.isEmpty();
    boolean pat = !patient.isEmpty() && patient.getValue().matches("[1-9][0-9]*");
    boolean time = !timepoint.isEmpty() && timepoint.getValue().matches("[1-9][0-9]*");
    boolean input = project && treat && pat && time;
    boolean res = false;
    if (input) {
      String extID = treatment.getValue().substring(0, 1) + ":0" + patient.getValue() + ":"
          + timepoint.getValue();
      res = !cases.contains(extID);
    }
    return res;
  }

  private MCCView getView() {
    return this;
  }

  private List<Property> parseXMLFactors(Sample s) {
    try {
      return p.getExpFactorsFromXML(s.getProperties().get("Q_PROPERTIES"));
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (JAXBException e) {
      e.printStackTrace();
    }
    logger.error("Error while parsing Q_PROPERTIES XML");
    return null;
  }

  private void initMCCListeners() {

    ValueChangeListener check = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -7015950228583952364L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        addSamples.setEnabled(allValid());
      }
    };
    treatment.addValueChangeListener(check);
    timepoint.addValueChangeListener(check);
    patient.addValueChangeListener(check);

    newProject.addValueChangeListener(new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -7747648379674835869L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        if (!newProject.isEmpty())
          counter = new SampleCounter(newProject.getValue());
        for (Sample s : openbis.getSamplesOfProject("/" + mccSpace + "/" + newProject.getValue()))
          counter.increment(s);
        addSamples.setEnabled(allValid());
      }
    });

    mccProjects.addValueChangeListener(new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 354186297920828100L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        projectBoxChanged();
      }
    });

    addSamples.addClickListener(new Button.ClickListener() {
      /**
       * 
       */
      private static final long serialVersionUID = -3356594300480093815L;

      @Override
      public void buttonClick(ClickEvent event) {
        logger.info("Adding MCC Patient.");
        List<List<ISampleBean>> samps = null;
        samps = prepDefaultMCCSamples();
        // for (List<ISampleBean> ss : samps)
        // for (ISampleBean s : ss)
        // logger.debug("Created samples: " + s);
        addSamples.setEnabled(false);
        creator.prepareXMLProps(samps);
        creator.registerProjectWithExperimentsAndSamplesBatchWise(samps, null,
            new ArrayList<OpenbisExperiment>(), bar, registerInfo,
            new RegisteredSamplesReadyRunnable(getView(), getView()), user, false);
      }
    });
  }

  protected void projectBoxChanged() {
    addSamples.setEnabled(true);
    if (mccProjects.getValue() == null) {
      newProject.setEnabled(true);
      treatment.setEnabled(true);
      addSamples.setEnabled(false);
    } else {
      newProject.setEnabled(false);
    }
    entities = new ArrayList<Sample>();
    cases = new HashSet<String>();
    if (newProject.getValue().isEmpty())
      counter = new SampleCounter((String) mccProjects.getValue());
    else
      counter = new SampleCounter(newProject.getValue());
    String treatment = "";
    boolean wrongFormat = false;
    for (Sample s : openbis.getSamplesWithParentsAndChildrenOfProjectBySearchService(
        "/" + mccSpace + "/" + (String) mccProjects.getValue())) {
      counter.increment(s);
      String id = s.getProperties().get("Q_EXTERNALDB_ID");
      if (s.getSampleTypeCode().equals("Q_BIOLOGICAL_ENTITY")) {
        entities.add(s);
        patients.add(id);
      } else {
        if (treatment.isEmpty()) {
          for (Property f : parseXMLFactors(s)) {
            if (f.getLabel().equals("treatment")) {
              treatment = f.getValue();
              getView().treatment.setValue(treatment);
              getView().treatment.setEnabled(false);
            }
          }
        }
        try {
          if (id != null) {
            String prefix = StringUtils.join(Arrays.copyOfRange(id.split(":"), 0, 3), ":");
            cases.add(prefix);
          }
        } catch (IndexOutOfBoundsException e) {
          wrongFormat = true;
        }
      }
    }
    addSamples.setEnabled(allValid());
    if (wrongFormat) {
      logger
          .warn("MCCView found samples with unexpected/empty External ID. Probably no problem...");
      // Functions.notification("Wrong format",
      // "Project doesn't fit the expected layout. Please choose another project.",
      // NotificationType.ERROR);
      // addSamples.setEnabled(false);
    }
    BeanItemContainer<MCCPatient> c = new BeanItemContainer<MCCPatient>(MCCPatient.class);
    for (String id : cases) {
      String[] idSplit = id.split(":");
      MCCPatient p = new MCCPatient(idSplit[1], treatment, idSplit[2]);
      c.addBean(p);
    }
    existingPatients.setContainerDataSource(c);
    existingPatients.setPageLength(Math.min(10, c.size()));
    existingPatients.sort(new Object[] {"ID", "timepoint"}, new boolean[] {true});
  }

  @SuppressWarnings("unchecked")
  private List<List<ISampleBean>> prepDefaultMCCSamples() {
    String timepoint = this.timepoint.getValue();
    String treatment = this.treatment.getValue();
    String patient = this.patient.getValue();
    List<List<ISampleBean>> res = new ArrayList<List<ISampleBean>>();
    List<ISampleBean> patients = new ArrayList<ISampleBean>();
    List<ISampleBean> urine = new ArrayList<ISampleBean>();
    List<ISampleBean> uAliquots = new ArrayList<ISampleBean>();
    List<ISampleBean> liver = new ArrayList<ISampleBean>();
    List<ISampleBean> plasma = new ArrayList<ISampleBean>();
    List<ISampleBean> pAliquots = new ArrayList<ISampleBean>();
    List<ISampleBean> molecules = new ArrayList<ISampleBean>();

    String project = (String) mccProjects.getValue();
    if (!newProject.isEmpty())
      project = newProject.getValue();

    String patientID = project + "ENTITY-" + patient;// new parent

    if (patient.length() < 2)
      patient = "0" + patient;
    String patientExtID = treatment.substring(0, 1).toUpperCase() + ":" + patient;

    HashMap<String, Object> metadata = new HashMap<String, Object>();
    // if new patient, add to samples to register
    if (!this.patients.contains(patientExtID)) {
      metadata.put("Q_EXTERNALDB_ID", patientExtID);
      metadata.put("Q_NCBI_ORGANISM", "9606");
      patients.add(new TSVSampleBean(patientID, project + "E1", project, mccSpace,
          "Q_BIOLOGICAL_ENTITY", "patient #" + patient, "", metadata));
    }
    String extIDBase = patientExtID + ":" + timepoint + ":";

    String urineExtIDBase = extIDBase + "U:1";
    metadata = new HashMap<String, Object>();
    metadata.put("XML_FACTORS",
        "treatment: " + treatment + "; timepoint: evaluation #" + timepoint);
    metadata.put("Q_PRIMARY_TISSUE", "URINE");
    metadata.put("Q_EXTERNALDB_ID", urineExtIDBase);
    String urineID = counter.getNewBarcode();// parent
    urine.add(new TSVSampleBean(urineID, project + "E2", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
        "urine sample", patientID, (HashMap<String, Object>) metadata.clone()));
    for (int i = 1; i < 6; i++) {
      char lower = (char) ('a' + i - 1);
      String ID = counter.getNewBarcode();
      metadata.put("Q_EXTERNALDB_ID", urineExtIDBase + lower);
      uAliquots.add(new TSVSampleBean(ID, project + "E3", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
          "aliquot #" + i, urineID, (HashMap<String, Object>) metadata.clone()));
    }
    for (int i = 1; i < 4; i++) {
      String plasmaExtID = extIDBase + "B:" + i;
      String plasmaID = counter.getNewBarcode();// parent
      metadata.put("Q_EXTERNALDB_ID", plasmaExtID);
      metadata.put("Q_PRIMARY_TISSUE", "BLOOD_PLASMA");
      plasma
          .add(new TSVSampleBean(plasmaID, project + "E4", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
              "EDTA plasma #" + i, patientID, (HashMap<String, Object>) metadata.clone()));
      if (i == 1) {
        for (int j = 1; j < 3; j++) {
          char lower = (char) ('a' + j - 1);
          String ID = counter.getNewBarcode();
          metadata.put("Q_EXTERNALDB_ID", plasmaExtID + lower);
          pAliquots
              .add(new TSVSampleBean(ID, project + "E4", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
                  "plasma aliquot #" + j, plasmaID, (HashMap<String, Object>) metadata.clone()));
        }
      }
      if (i == 3) {
        metadata.remove("Q_PRIMARY_TISSUE");
        for (int j = 1; j < 5; j++) {
          char lower = (char) ('a' + j - 1);
          String ID = counter.getNewBarcode();
          metadata.put("Q_EXTERNALDB_ID", plasmaExtID + lower);
          metadata.put("Q_SAMPLE_TYPE", "SMALLMOLECULES");
          molecules.add(new TSVSampleBean(ID, project + "E5", project, mccSpace, "Q_TEST_SAMPLE",
              "cryovial #" + j, plasmaID, (HashMap<String, Object>) metadata.clone()));
        }
        metadata.remove("Q_SAMPLE_TYPE");
      }
    }
    String tumorExtBase = extIDBase + "T";
    for (int i = 1; i < 9; i++) {
      String ID = counter.getNewBarcode();
      metadata.put("Q_EXTERNALDB_ID", tumorExtBase + i);
      metadata.put("Q_PRIMARY_TISSUE", "HEPATOCELLULAR_CARCINOMA");
      liver.add(new TSVSampleBean(ID, project + "E6", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
          "tumor biopsy #" + i, patientID, (HashMap<String, Object>) metadata.clone()));
    }
    String liverExtBase = extIDBase + "L";
    for (int i = 1; i < 5; i++) {
      String ID = counter.getNewBarcode();
      metadata.put("Q_EXTERNALDB_ID", liverExtBase + i);
      metadata.put("Q_PRIMARY_TISSUE", "LIVER");
      liver.add(new TSVSampleBean(ID, project + "E6", project, mccSpace, "Q_BIOLOGICAL_SAMPLE",
          "liver biopsy #" + i, patientID, (HashMap<String, Object>) metadata.clone()));
    }
    List<List<ISampleBean>> dummy = new ArrayList<List<ISampleBean>>(
        Arrays.asList(patients, urine, uAliquots, plasma, pAliquots, molecules, liver));
    for (List<ISampleBean> l : dummy)
      if (l.size() > 0)
        res.add(l);
    return res;
  }

  protected List<String> sampsToStrings(List<Sample> children) {
    List<String> res = new ArrayList<String>();
    for (Sample c : children)
      res.add(c.getCode());
    return res;
  }

  @Override
  //TODO handle errors
  public void registrationDone(boolean sqlDown, String errors) {
    logger.info("Registration complete, reloading patient table.");
    Styles.notification("Registration complete!", "Registration of patient complete.",
        NotificationType.SUCCESS);
    projectBoxChanged();
  }

  @Override
  public void performPostRegistrationTasks(boolean success) {
  }

  @Override
  public String getRegistrationError() {
    return creator.getErrors();
  }
}
