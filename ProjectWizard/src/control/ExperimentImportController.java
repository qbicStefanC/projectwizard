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
package control;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang.StringUtils;

import registration.OpenbisCreationController;
import uicomponents.MissingInfoComponent;
import uicomponents.ProjectInformationComponent;
import uicomponents.Styles;
import uicomponents.Styles.NotificationType;
import views.StandaloneTSVImport;

import logging.Log4j2Logger;
import main.SamplePreparator;
import main.SampleSummaryBean;
import main.VocabularyValidator;
import model.DesignType;
import model.ExperimentType;
import model.ISampleBean;
import model.MHCTyping;
import model.OpenbisExperiment;
import model.PersonType;
import model.ProjectInfo;
import model.TSVSampleBean;
import processes.RegisteredSamplesReadyRunnable;

import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.validator.CompositeValidator;
import com.vaadin.data.validator.RegexpValidator;
import com.vaadin.event.FieldEvents.FocusEvent;
import com.vaadin.event.FieldEvents.FocusListener;
import com.vaadin.ui.Button;
import com.vaadin.ui.Upload;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Component;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Upload.FinishedEvent;
import com.vaadin.ui.Upload.FinishedListener;

import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Experiment;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Project;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;
import io.DBManager;
import io.DBVocabularies;
import life.qbic.openbis.openbisclient.IOpenBisClient;


public class ExperimentImportController implements IRegistrationController {

  private StandaloneTSVImport view;
  private final Uploader uploader = new Uploader();
  private OpenbisCreationController openbisCreator;
  private SamplePreparator prep;

  private ProjectInfo projectInfo;
  private Map<String, Map<String, Object>> msProperties;
  private Map<String, Map<String, Object>> mhcProperties;
  private Map<String, MHCTyping> dnaSampleCodeToMHCType;
  private Map<String, Sample> extIDToSample;
  private List<OpenbisExperiment> complexExperiments;

  private Map<String, String> reverseTaxMap;
  private Map<String, String> taxMap;
  private Map<String, String> reverseTissueMap;
  private Map<String, String> tissueMap;
  private List<String> analytesVocabulary;
  private MissingInfoComponent questionaire;
  private IOpenBisClient openbis;
  private DBManager dbm;
  private DBVocabularies vocabs;
  private int firstFreeExperimentID;
  private int firstFreeEntityID;
  private String firstFreeBarcode;
  private String nextBarcode;
  Map<String, String> extCodeToBarcode;

  logging.Logger logger = new Log4j2Logger(ExperimentImportController.class);

  public ExperimentImportController(OpenbisCreationController creator, DBVocabularies vocabularies,
      IOpenBisClient openbis, DBManager dbm) {
    view = new StandaloneTSVImport();
    this.dbm = dbm;
    this.questionaire = view.getMissingInfoComponent();
    this.vocabs = vocabularies;
    this.openbis = openbis;
    this.taxMap = vocabularies.getTaxMap();
    this.tissueMap = vocabularies.getTissueMap();
    this.analytesVocabulary = vocabularies.getAnalyteTypes();
    this.reverseTaxMap = new HashMap<String, String>();
    for (Map.Entry<String, String> entry : taxMap.entrySet()) {
      this.reverseTaxMap.put(entry.getValue(), entry.getKey());
    }
    this.reverseTissueMap = new HashMap<String, String>();
    for (Map.Entry<String, String> entry : tissueMap.entrySet()) {
      this.reverseTissueMap.put(entry.getValue(), entry.getKey());
    }
    this.openbisCreator = creator;
  }

  public void init(final String user) {
    ExperimentImportController control = this;
    Upload upload = new Upload("Upload your file here", uploader);
    view.initView(upload);
    upload.setButtonCaption("Upload");
    // Listen for events regarding the success of upload.
    upload.addFailedListener(uploader);
    upload.addSucceededListener(uploader);
    FinishedListener uploadFinListener = new FinishedListener() {
      /**
       * 
       */
      private static final long serialVersionUID = -8413963075202260180L;

      public void uploadFinished(FinishedEvent event) {
        String uploadError = uploader.getError();
        File file = uploader.getFile();
        view.resetAfterUpload();
        if (file.getPath().endsWith("up_")) {
          String msg = "No file selected.";
          logger.warn(msg);
          Styles.notification("Failed to read file.", msg, NotificationType.ERROR);
          if (!file.delete())
            logger.error("uploaded tmp file " + file.getAbsolutePath() + " could not be deleted!");
        } else {
          if (uploadError == null || uploadError.isEmpty()) {
            String msg = "Upload successful!";
            logger.info(msg);
            try {
              view.setRegEnabled(false);
              prep = new SamplePreparator();
              Map<String, Set<String>> experimentTypeVocabularies =
                  new HashMap<String, Set<String>>();
              experimentTypeVocabularies.put("Q_ANTIBODY", vocabs.getAntibodiesMap().keySet());
              experimentTypeVocabularies.put("Q_CHROMATOGRAPHY_TYPE",
                  vocabs.getChromTypesMap().keySet());
              experimentTypeVocabularies.put("Q_MS_DEVICE",
                  new HashSet<String>(vocabs.getDeviceMap().values()));
              experimentTypeVocabularies.put("Q_MS_LCMS_METHOD",
                  new HashSet<String>(vocabs.getLcmsMethods()));

              VocabularyValidator validator = new VocabularyValidator(experimentTypeVocabularies);
              boolean readSuccess = prep.processTSV(file, getImportType());
              boolean vocabValid = false;
              if (readSuccess) {
                msProperties = prep.getSpecialExperimentsOfTypeOrNull("Q_MS_MEASUREMENT");
                mhcProperties = prep.getSpecialExperimentsOfTypeOrNull("Q_MHC_LIGAND_EXTRACTION");
                List<Map<String, Object>> metadataList = new ArrayList<Map<String, Object>>();
                if (msProperties != null)
                  metadataList.addAll(msProperties.values());
                if (mhcProperties != null)
                  metadataList.addAll(mhcProperties.values());
                vocabValid = validator.validateExperimentMetadata(metadataList);
              }
              if (readSuccess && vocabValid) {
                List<SampleSummaryBean> summaries = prep.getSummary();
                for (SampleSummaryBean s : summaries) {
                  String translation = reverseTaxMap.get(s.getFullSampleContent());
                  if (translation != null)
                    s.setSampleContent(translation);
                }
                Styles.notification("Upload successful",
                    "Experiment was successfully uploaded and read.", NotificationType.SUCCESS);
                switch (getImportType()) {
                  // Standard hierarchic QBiC design
                  case QBIC:
                    view.setSummary(summaries);
                    view.setProcessed(prep.getProcessed());
                    view.setRegEnabled(true);
                    projectInfo = prep.getProjectInfo();
                    break;
                  // Standard non-hierarchic design without QBiC specific keywords
                  case Standard:
                    Map<String, List<String>> catToVocabulary = new HashMap<String, List<String>>();
                    catToVocabulary.put("Species", new ArrayList<String>(taxMap.keySet()));
                    catToVocabulary.put("Tissues", new ArrayList<String>(tissueMap.keySet()));
                    catToVocabulary.put("Analytes", new ArrayList<String>(analytesVocabulary));
                    Map<String, List<String>> missingCategoryToValues =
                        new HashMap<String, List<String>>();
                    missingCategoryToValues.put("Species",
                        new ArrayList<String>(prep.getSpeciesSet()));
                    missingCategoryToValues.put("Tissues",
                        new ArrayList<String>(prep.getTissueSet()));
                    missingCategoryToValues.put("Analytes",
                        new ArrayList<String>(prep.getAnalyteSet()));
                    initMissingInfoListener(prep, missingCategoryToValues, catToVocabulary);
                    break;
                  // MHC Ligands that have already been measured (Filenames exist)
                  case MHC_Ligands_Finished:
                    catToVocabulary = new HashMap<String, List<String>>();
                    catToVocabulary.put("Species", new ArrayList<String>(taxMap.keySet()));
                    catToVocabulary.put("Tissues", new ArrayList<String>(tissueMap.keySet()));
                    catToVocabulary.put("Analytes", new ArrayList<String>(analytesVocabulary));
                    missingCategoryToValues = new HashMap<String, List<String>>();
                    missingCategoryToValues.put("Species",
                        new ArrayList<String>(prep.getSpeciesSet()));
                    missingCategoryToValues.put("Tissues",
                        new ArrayList<String>(prep.getTissueSet()));
                    missingCategoryToValues.put("Analytes",
                        new ArrayList<String>(prep.getAnalyteSet()));
                    initMissingInfoListener(prep, missingCategoryToValues, catToVocabulary);
                    break;
                  default:
                    logger.error("Error parsing tsv: " + prep.getError());
                    // view.showError(prep.getError());
                    Styles.notification("Failed to read file.", prep.getError(),
                        NotificationType.ERROR);
                    break;
                }
              } else {
                if (!readSuccess) {
                  String error = prep.getError();
                  Styles.notification("Failed to read file.", error, NotificationType.ERROR);
                } else {
                  String error = validator.getError();
                  Styles.notification("Failed to process file.", error, NotificationType.ERROR);
                }
                if (!file.delete())
                  logger.error(
                      "uploaded tmp file " + file.getAbsolutePath() + " could not be deleted!");
              }
            } catch (IOException e) {
              e.printStackTrace();
            }
          } else {
            // view.showError(error);
            Styles.notification("Failed to upload file.", uploadError, NotificationType.ERROR);
            if (!file.delete())
              logger
                  .error("uploaded tmp file " + file.getAbsolutePath() + " could not be deleted!");
          }
        }
      }
    };
    upload.addFinishedListener(uploadFinListener);
    // view.initUpload(upload);

    Button.ClickListener cl = new Button.ClickListener() {
      /**
       * 
       */
      private static final long serialVersionUID = 1L;

      /**
       * 
       */

      @Override
      public void buttonClick(ClickEvent event) {
        String src = event.getButton().getCaption();
        if (src.equals("Register All")) {
          view.getRegisterButton().setEnabled(false);
          view.showRegistration();
          // collect experiment information
          complexExperiments = new ArrayList<OpenbisExperiment>();
          complexExperiments
              .addAll(collectComplexExperiments(msProperties, ExperimentType.Q_MS_MEASUREMENT));
          complexExperiments.addAll(
              collectComplexExperiments(mhcProperties, ExperimentType.Q_MHC_LIGAND_EXTRACTION));
          openbisCreator.registerProjectWithExperimentsAndSamplesBatchWise(view.getSamples(),
              projectInfo.getDescription(), complexExperiments, view.getProgressBar(),
              view.getProgressLabel(), new RegisteredSamplesReadyRunnable(view, control), user,
              projectInfo.isPilot());
          List<String> tsv = prep.getOriginalTSV();
          switch (getImportType()) {
            case Standard:
            case MHC_Ligands_Finished:
              String tsvContent = addBarcodesToTSV(tsv, view.getSamples(), getImportType());
              view.setTSVWithBarcodes(tsvContent,
                  uploader.getFileNameWithoutExtension() + "_with_barcodes");
              break;
            default:
              break;
          }
        }
      }

      private Collection<? extends OpenbisExperiment> collectComplexExperiments(
          Map<String, Map<String, Object>> propsMap, ExperimentType type) {
        List<OpenbisExperiment> res = new ArrayList<OpenbisExperiment>();
        if (propsMap != null) {
          for (String code : propsMap.keySet())
            res.add(new OpenbisExperiment(code, type, propsMap.get(code)));
        }
        return res;
      }
    };
    view.getRegisterButton().addClickListener(cl);
  }

  protected String addBarcodesToTSV(List<String> tsv, List<List<ISampleBean>> levels,
      DesignType designType) {
    StringBuilder builder = new StringBuilder(5000);
    switch (designType) {
      case Standard:
        int extIDPos = -1;
        for (String line : tsv) {
          String[] splt = line.split("\t");
          if (extIDPos < 0) {
            extIDPos = Arrays.asList(splt).indexOf("Analyte ID");// TODO generalize?
            builder.append("QBiC Code\t" + line + "\n");
          } else {
            String extID = splt[extIDPos];
            String code = extCodeToBarcode.get(extID);// .getCode();
            builder.append(code + "\t" + line + "\n");
          }
        }
        break;
      case MHC_Ligands_Finished:
        Map<String, String> fileNameToBarcode = new HashMap<String, String>();
        for (List<ISampleBean> samples : levels) {
          for (ISampleBean s : samples) {
            if (s.getType().equals("Q_MS_RUN")) {
              Map<String, Object> props = s.getMetadata();
              fileNameToBarcode.put(props.get("File").toString(), s.getCode());
              props.remove("File");
            }
          }
        }
        int filePos = -1;
        for (String line : tsv) {
          String[] splt = line.split("\t");
          if (filePos < 0) {
            filePos = Arrays.asList(splt).indexOf("Filename");// TODO generalize?
            builder.append("QBiC Code\t" + line + "\n");
          } else {
            String file = splt[filePos];
            String code = fileNameToBarcode.get(file);
            builder.append(code + "\t" + line + "\n");
          }
        }
      default:
        break;
    }
    return builder.toString();
  }

  protected void initMissingInfoListener(SamplePreparator prep,
      Map<String, List<String>> missingCategoryToValues,
      Map<String, List<String>> catToVocabulary) {
    extCodeToBarcode = new HashMap<String, String>();

    // TODO where is this added? does it need to be added?
    ProjectInformationComponent projectInfoComponent =
        new ProjectInformationComponent(vocabs.getSpaces(), vocabs.getPeople().keySet());

    ValueChangeListener missingInfoFilledListener = new ValueChangeListener() {

      @Override
      public void valueChange(ValueChangeEvent event) {
        boolean overflow = false;
        boolean infoComplete = questionaire.isValid();
        boolean samplesToRegister = false;
        if (infoComplete) {
          List<SampleSummaryBean> summaries = prep.getSummary();
          for (SampleSummaryBean b : summaries) {
            String cat = "";
            if (b.getSampleType().contains("Source"))
              cat = "Species";
            else if (b.getSampleType().contains("Sample Extract"))
              cat = "Tissues";
            else if (b.getSampleType().contains("Preparations"))
              cat = "Analytes";
            if (missingCategoryToValues.containsKey(cat)) {
              String val = b.getFullSampleContent();
              List<String> newVal = new ArrayList<String>();
              for (String v : val.split(", ")) {
                v = v.trim();
                String translation = questionaire.getVocabularyLabelForValue(cat, v);
                if (translation == null)
                  translation = reverseTissueMap.get(v);
                if (translation == null)
                  translation = v;
                newVal.add(translation);
              }
              b.setSampleContent(StringUtils.join(newVal, ", "));
            }
          }
          view.setSummary(summaries);
          projectInfo = projectInfoComponent.getProjectInfo();
          String space = questionaire.getSpaceCode();
          String project = questionaire.getProjectCode();
          try {
            countExistingOpenbisEntities(space, project);
          } catch (TooManySamplesException e1) {
            // TODO Auto-generated catch block
            overflow = true;
          }

          int entityNum = firstFreeEntityID;
          nextBarcode = null;

          List<List<ISampleBean>> processed = prep.getProcessed();
          Set<String> msCodes = new HashSet<String>();
          dnaSampleCodeToMHCType = new HashMap<String, MHCTyping>();
          Map<String, String> specialExpToExpCode = new HashMap<String, String>();
          for (List<ISampleBean> level : processed) {
            String type = level.get(0).getType();
            String exp = "";
            if (!type.equals("Q_MS_RUN") && !type.equals("Q_MHC_LIGAND_EXTRACT"))
              exp = getNextExperiment(project);
            // list of existing samples to be removed before registration
            List<ISampleBean> existing = new ArrayList<ISampleBean>();
            for (ISampleBean b : level) {
              TSVSampleBean t = (TSVSampleBean) b;
              String extID = (String) t.getMetadata().get("Q_EXTERNALDB_ID");
              if (extIDToSample.containsKey(extID)) {
                existing.add(t);
                extCodeToBarcode.put(extID, extIDToSample.get(extID).getCode());
              } else {
                t.setProject(project);
                t.setSpace(space);
                String code = "";
                Map<String, Object> props = t.getMetadata();
                switch (t.getType()) {
                  case "Q_BIOLOGICAL_ENTITY":
                    code = project + "ENTITY-" + entityNum;
                    String newVal = questionaire.getVocabularyLabelForValue("Species",
                        props.get("Q_NCBI_ORGANISM"));
                    props.put("Q_NCBI_ORGANISM", taxMap.get(newVal));
                    entityNum++;
                    break;
                  case "Q_BIOLOGICAL_SAMPLE":
                    try {
                      incrementOrCreateBarcode(project);
                    } catch (TooManySamplesException e) {
                      overflow = true;
                    }
                    code = nextBarcode;
                    newVal = questionaire.getVocabularyLabelForValue("Tissues",
                        props.get("Q_PRIMARY_TISSUE"));
                    props.put("Q_PRIMARY_TISSUE", tissueMap.get(newVal));
                    break;
                  case "Q_TEST_SAMPLE":
                    try {
                      incrementOrCreateBarcode(project);
                    } catch (TooManySamplesException e) {
                      overflow = true;
                    }
                    code = nextBarcode;
                    newVal = questionaire.getVocabularyLabelForValue("Analytes",
                        props.get("Q_SAMPLE_TYPE"));
                    props.put("Q_SAMPLE_TYPE", newVal);
                    if (getImportType().equals(DesignType.MHC_Ligands_Finished)) {
                      if ("DNA".equals(newVal)) {
                        List<String> c1 = (List<String>) props.get("MHC_I");
                        List<String> c2 = (List<String>) props.get("MHC_II");
                        dnaSampleCodeToMHCType.put(code, new MHCTyping(c1, c2));
                        props.remove("MHC_I");
                        props.remove("MHC_II");
                      }
                    }
                    break;
                  case "Q_MHC_LIGAND_EXTRACT":
                    try {
                      incrementOrCreateBarcode(project);
                    } catch (TooManySamplesException e) {
                      overflow = true;
                    }
                    code = nextBarcode;
                    if (!specialExpToExpCode.containsKey(t.getExperiment())) {
                      specialExpToExpCode.put(t.getExperiment(), getNextExperiment(project));
                    }
                    exp = specialExpToExpCode.get(t.getExperiment());
                    break;
                  case "Q_MS_RUN":
                    // get ms experiment to connect it correctly
                    if (!specialExpToExpCode.containsKey(t.getExperiment())) {
                      specialExpToExpCode.put(t.getExperiment(), getNextExperiment(project));
                    }
                    exp = specialExpToExpCode.get(t.getExperiment());
                    // get parent sample for code
                    String parentExtID = t.fetchParentIDs().get(0);
                    String parentCode = extCodeToBarcode.get(parentExtID);// .getCode();
                    int msRun = 1;
                    code = "";
                    while (code.isEmpty() || msCodes.contains(code)) {
                      code = "MS" + Integer.toString(msRun) + parentCode;
                      msRun++;
                    }
                    msCodes.add(code);
                    break;
                }
                t.setExperiment(exp);
                t.setCode(code);
                extCodeToBarcode.put((String) props.get("Q_EXTERNALDB_ID"), code);// t);
                List<String> parents = t.fetchParentIDs();
                t.setParents("");
                for (String parentExtID : parents) {
                  if (extCodeToBarcode.containsKey(parentExtID))
                    t.addParent(extCodeToBarcode.get(parentExtID));// .getCode());
                  else
                    logger.warn(
                        "Parent could not be translated, because no ext id to code mapping was found for ext id "
                            + parentExtID);
                }
              }
            }
            // remove existing samples from registration process
            level.removeAll(existing);
            samplesToRegister |= !level.isEmpty();
          }
          fixSpecialExperiments(specialExpToExpCode);
          view.setProcessed(processed);
        }
        view.setRegEnabled(infoComplete && samplesToRegister && !overflow);
        if (infoComplete) {
          if (!samplesToRegister) {
            Styles.notification("Samples already exist.",
                "Every Analyte ID was already found in existing samples of this project.",
                NotificationType.DEFAULT);
          }
          if (overflow) {
            Styles.notification("Too many samples.",
                "This experiment exceeds the maximum number of samples for one project.",
                NotificationType.ERROR);
          }
        }
      }

      private void fixSpecialExperiments(Map<String, String> specialExpToExpCode) {
        Set<String> codes = new HashSet<String>();
        if (mhcProperties != null) {
          codes.addAll(mhcProperties.keySet());
          for (String code : codes) {
            mhcProperties.put(specialExpToExpCode.get(code), mhcProperties.get(code));
            mhcProperties.remove(code);
          }
        }
        codes.clear();
        if (msProperties != null) {
          codes.addAll(msProperties.keySet());
          for (String code : codes) {
            msProperties.put(specialExpToExpCode.get(code), msProperties.get(code));
            msProperties.remove(code);
          }
        }
      }
    };
    questionaire = view.initMissingInfoComponent(projectInfoComponent, missingCategoryToValues,
        catToVocabulary, missingInfoFilledListener);
    // view.addComponent(questionaire);

    // add project code validators
    TextField f = projectInfoComponent.getProjectField();
    CompositeValidator vd = new CompositeValidator();
    RegexpValidator p = new RegexpValidator("Q[A-Xa-x0-9]{4}",
        "Project must have length of 5, start with Q and not contain Y or Z");
    vd.addValidator(p);
    vd.addValidator(new ProjectNameValidator(openbis));
    f.addValidator(vd);
    f.setImmediate(true);
    f.setValidationVisible(true);

    FocusListener fListener = new FocusListener() {
      private static final long serialVersionUID = 8721337946386845992L;

      @Override
      public void focus(FocusEvent event) {
        TextField pr = projectInfoComponent.getProjectField();
        if (!pr.isValid() || pr.isEmpty()) {
          // new project selected...keep generating codes until one is valid
          projectInfoComponent.tryEnableCustomProject(generateUnusedProjectCode());
        }
      }
    };
    projectInfoComponent.getProjectField().addFocusListener(fListener);

    Button.ClickListener projCL = new Button.ClickListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -6646294420820222646L;

      @Override
      public void buttonClick(ClickEvent event) {
        String existingProject = (String) projectInfoComponent.getProjectBox().getValue();
        if (existingProject == null || existingProject.isEmpty()) {
          projectInfoComponent.tryEnableCustomProject(generateUnusedProjectCode());
        }
      }
    };
    projectInfoComponent.getProjectReloadButton().addClickListener(projCL);

    questionaire.getSpaceBox().addValueChangeListener(new ValueChangeListener() {

      @Override
      public void valueChange(ValueChangeEvent event) {
        questionaire.resetProjects();
        String space = questionaire.getSpaceCode();
        if (space != null) {
          List<String> projects = new ArrayList<String>();
          for (Project p : openbis.getProjectsOfSpace(space)) {
            String code = p.getCode();
            // String name = dbm.getProjectName("/" + space + "/" + code);
            // if (name != null && name.length() > 0) {
            // if (name.length() >= 80)
            // name = name.substring(0, 80) + "...";
            // code += " (" + name + ")";
            // }
            projects.add(code);
          }
          questionaire.setProjectCodes(projects);
        }
      }
    });
  }

  private String getNextExperiment(String project) {
    String res = project + "E" + firstFreeExperimentID;
    firstFreeExperimentID++;
    return res;
  }

  private String generateUnusedProjectCode() {
    Random r = new Random();
    String res = "";
    while (res.length() < 5 || openbis.getProjectByCode(res) != null) {
      res = "Q";
      for (int i = 1; i < 5; i++) {
        char c = 'Y';
        while (c == 'Y' || c == 'Z')
          c = (char) (r.nextInt(26) + 'A');
        res += c;
      }
    }
    return res;
  }

  /**
   * Fetches context information like space and project and computes first unused IDs of samples and
   * context. Also fills a map of existing secondary names and samples used later not to re-register
   * 
   * @throws TooManySamplesException
   */
  private void countExistingOpenbisEntities(String space, String project)
      throws TooManySamplesException {
    extIDToSample = new HashMap<String, Sample>();
    firstFreeExperimentID = 1;
    firstFreeEntityID = 1;
    firstFreeBarcode = "";// TODO cleanup where not needed
    List<Sample> samples = new ArrayList<Sample>();
    if (openbis.projectExists(space, project)) {
      samples.addAll(openbis
          .getSamplesWithParentsAndChildrenOfProjectBySearchService("/" + space + "/" + project));
    }
    for (Experiment e : openbis.getExperimentsOfProjectByCode(project)) {
      String code = e.getCode();
      String[] split = code.split(project + "E");
      if (code.startsWith(project + "E") && split.length > 1) {
        int num = 0;
        try {
          num = Integer.parseInt(split[1]);
        } catch (Exception e2) {
        }
        if (firstFreeExperimentID <= num)
          firstFreeExperimentID = num + 1;
      }
    }
    for (Sample s : samples) {
      String code = s.getCode();
      // collect existing samples by their secondary name
      String secondaryName = s.getProperties().get("Q_SECONDARY_NAME");
      if (extIDToSample.containsKey(secondaryName))
        logger.warn(secondaryName
            + " was found as a secondary name for multiple samples. This might"
            + " lead to inconsistencies if new samples are to be attached to this secondary name.");
      extIDToSample.put(secondaryName, s);
      if (Functions.isQbicBarcode(code)) {
        if (Functions.compareSampleCodes(firstFreeBarcode, code) <= 0) {
          firstFreeBarcode = Functions.incrementSampleCode(code);
          String firstBarcode = project + "001A" + Functions.checksum(project + "001A");
          if (firstBarcode.equals(firstFreeBarcode))
            throw new TooManySamplesException();
        }
      } else if (s.getSampleTypeCode().equals(("Q_BIOLOGICAL_ENTITY"))) {
        int num = Integer.parseInt(s.getCode().split("-")[1]);
        if (num >= firstFreeEntityID)
          firstFreeEntityID = num + 1;
      }
    }
  }

  private void incrementOrCreateBarcode(String project) throws TooManySamplesException {
    String firstBarcode = project + "001A" + Functions.checksum(project + "001A");
    if (nextBarcode == null) {
      if (firstFreeBarcode.isEmpty()) {
        String base = project + Functions.createCountString(1, 3) + "A";
        firstFreeBarcode = base + Functions.checksum(base);
      }
      nextBarcode = firstFreeBarcode;
    } else {
      nextBarcode = Functions.incrementSampleCode(nextBarcode);
      if (nextBarcode.equals(firstBarcode))
        throw new TooManySamplesException();
    }
  }

  protected DesignType getImportType() {
    return view.getSelectedDesignOption();
  }

  @Override
  public void performPostRegistrationTasks(boolean success) {
    if (success) {
      String space = null;
      String project = null;
      String projectName = null;

      if (getImportType().equals(DesignType.QBIC)) {
        projectName = projectInfo.getSecondaryName();
        ISampleBean s = view.getSamples().get(0).get(0);
        project = s.getProject();
        space = s.getSpace();
      } else {
        space = questionaire.getSpaceCode();
        project = questionaire.getProjectCode();
        projectName = questionaire.getProjectSecondaryName();
        addPeopleAndProjectToDB("/" + space + "/" + project, projectName);
      }
      // TODO
      // for (OpenbisExperiment e : exps) {
      // if (e.getType().equals(ExperimentType.Q_EXPERIMENTAL_DESIGN)) {
      // String id = projectIdentifier + "/" + e.getOpenbisName();
      // for (Note n : notes) {
      // writeNoteToOpenbis(id, n);
      // }
      // }
      // }
      logger.info("Performing post registration tasks");
      if (dnaSampleCodeToMHCType != null)
        registerHLATypings(space);
      // TODO
      // for (OpenbisExperiment e : exps) {
      // String identifier = projectIdentifier + "/" + e.getOpenbisName();
      // int expID = dbm.addExperimentToDB(identifier);
      // if (e.getPersonID() > -1) {
      // int person = e.getPersonID();
      // dbm.addPersonToExperiment(expID, person, "Contact");
      // }
      // }
    }
  }

  private void addPeopleAndProjectToDB(String projectIdentifier, String projectName) {
    int projectID = dbm.addProjectToDB(projectIdentifier, projectName);
    int investigatorID = -1;
    int contactID = -1;
    int managerID = -1;
    Map<String, Integer> people = vocabs.getPeople();
    if (!questionaire.getPerson(PersonType.Investigator).equals(""))
      investigatorID = people.get(questionaire.getPerson(PersonType.Investigator));
    if (!questionaire.getPerson(PersonType.Manager).equals(""))
      managerID = people.get(questionaire.getPerson(PersonType.Manager));
    if (!questionaire.getPerson(PersonType.Contact).equals(""))
      contactID = people.get(questionaire.getPerson(PersonType.Contact));
    if (investigatorID != -1)
      dbm.addPersonToProject(projectID, investigatorID, "PI");
    if (contactID != -1)
      dbm.addPersonToProject(projectID, contactID, "Contact");
    if (managerID != -1)
      dbm.addPersonToProject(projectID, managerID, "Manager");
  }

  private void registerHLATypings(String space) {
    Map<String, Object> props = new HashMap<String, Object>();
    props.put("dropbox", "QBiC-register-hlatyping");
    for (String code : dnaSampleCodeToMHCType.keySet()) {
      props.put("filename", space + "_" + code + "_1.alleles");
      List<String> content = dnaSampleCodeToMHCType.get(code).getClassOne();
      props.put("content", content);
      openbis.triggerIngestionService("create-register-textfile", props);

      props.put("filename", space + "_" + code + "_2.alleles");
      content = dnaSampleCodeToMHCType.get(code).getClassTwo();
      props.put("content", content);
      openbis.triggerIngestionService("create-register-textfile", props);
    }
  }

  public Component getView() {
    return view;
  }

  @Override
  public String getRegistrationError() {
    return openbisCreator.getErrors();
  }
}
