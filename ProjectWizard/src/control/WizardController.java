package control;


import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Random;

import javax.xml.bind.JAXBException;

import logging.Log4j2Logger;
import main.OpenBisClient;
import main.OpenbisCreationController;
import main.SamplePreparator;
import model.AOpenbisSample;
import model.ExperimentBean;
import model.ExperimentType;
import model.NewSampleModelBean;

import org.vaadin.teemu.wizards.Wizard;
import org.vaadin.teemu.wizards.WizardStep;
import org.vaadin.teemu.wizards.event.WizardCancelledEvent;
import org.vaadin.teemu.wizards.event.WizardCompletedEvent;
import org.vaadin.teemu.wizards.event.WizardProgressListener;
import org.vaadin.teemu.wizards.event.WizardStepActivationEvent;
import org.vaadin.teemu.wizards.event.WizardStepSetChangedEvent;

import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Experiment;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Project;
import ch.systemsx.cisd.openbis.generic.shared.api.v1.dto.Sample;

import com.vaadin.data.Validator;
import com.vaadin.data.Property.ValueChangeEvent;
import com.vaadin.data.Property.ValueChangeListener;
import com.vaadin.data.validator.CompositeValidator;
import com.vaadin.data.validator.RegexpValidator;
import com.vaadin.event.FieldEvents.FocusEvent;
import com.vaadin.event.FieldEvents.FocusListener;
import com.vaadin.server.FileDownloader;
import com.vaadin.server.StreamResource;
import com.vaadin.ui.Button;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Notification;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.TextField;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.themes.ValoTheme;

import steps.ConditionInstanceStep;
import steps.EntityStep;
import steps.ExtractionStep;
import steps.PoolingStep;
import steps.ProjectContextStep;
import steps.TailoringStep;
import steps.TestStep;
import steps.SummaryRegisterStep;
import uicomponents.ProjectSelectionComponent;
import properties.Factor;

/**
 * Controller for the sample/experiment creation wizard
 * 
 * @author Andreas Friedrich
 * 
 */
public class WizardController {

  private OpenBisClient openbis;
  private OpenbisCreationController openbisCreator;
  private Wizard w;
  private Map<Steps, WizardStep> steps;
  private WizardDataAggregator dataAggregator;
  private boolean bioFactorInstancesSet = false;
  private boolean extractFactorInstancesSet = false;
  private boolean extractPoolsSet = false;
  private boolean testPoolsSet = false;
  private Map<String, String> taxMap;
  private Map<String, String> tissueMap;
  private List<String> measureTypes;
  private List<String> spaces;
  private FileDownloader tsvDL;
  private FileDownloader graphDL;
  SamplePreparator prep = new SamplePreparator();
  protected List<String> designExperimentTypes = new ArrayList<String>(Arrays.asList(
      "Q_EXPERIMENTAL_DESIGN", "Q_SAMPLE_EXTRACTION", "Q_SAMPLE_PREPARATION"));

  logging.Logger logger = new Log4j2Logger(WizardController.class);

  /**
   * 
   * @param openbis OpenBisClient API
   * @param taxMap Map containing the NCBI taxonomy (labels and ids) taken from openBIS
   * @param tissueMap Map containing the tissue
   * @param sampleTypes List containing the different sample (technology) types
   * @param spaces List of space names existing in openBIS
   */
  public WizardController(OpenBisClient openbis, Map<String, String> taxMap,
      Map<String, String> tissueMap, List<String> sampleTypes, List<String> spaces) {
    this.openbis = openbis;
    this.openbisCreator = new OpenbisCreationController(openbis);
    this.taxMap = taxMap;
    this.tissueMap = tissueMap;
    this.measureTypes = sampleTypes;
    this.spaces = spaces;
  }

  public class ProjectNameValidator implements Validator {
    /**
   * 
   */
    private static final long serialVersionUID = -321169606539673206L;

    @Override
    public void validate(Object value) throws InvalidValueException {
      String val = (String) value;
      if (!val.isEmpty() && val != null)
        if (openbis.getProjectByCode(val.toUpperCase()) != null)
          throw new InvalidValueException("Project code already in use");
    }
  }

  // Functions to add steps to the wizard depending on context
  // - - - - - - - - - - - - - - - - - - - - - - - - - - - - -

  private void setUpLoadStep() {
    w.addStep(steps.get(Steps.Registration)); // tsv upload and registration
  }

  private void setInheritEntities() {
    w.addStep(steps.get(Steps.Entity_Tailoring)); // entity negative selection
    w.addStep(steps.get(Steps.Extraction)); // extract first step
    setInheritExtracts();
  }

  private void setInheritExtracts() {
    w.addStep(steps.get(Steps.Extract_Tailoring)); // extracts negative selection
    w.addStep(steps.get(Steps.Test_Samples)); // test samples first step
    setUpLoadStep();
  }

  private void setExtractsPooling() {
    w.addStep(steps.get(Steps.Extract_Pooling)); // pooling step
    setTestStep();
  }

  private void setTestStep() {
    w.addStep(steps.get(Steps.Test_Samples)); // test samples first step
    setUpLoadStep();
  }

  private void setTestsPooling() {
    w.addStep(steps.get(Steps.Test_Sample_Pooling));
    setUpLoadStep();
  }

  private void setCreateEntities() {
    w.addStep(steps.get(Steps.Entities)); // entities first step
    setInheritEntities();
  }

  private void setEntityConditions() {
    w.addStep(steps.get(Steps.Entity_Conditions)); // entity conditions
    setInheritEntities();
  }

  private void setExtractConditions() {
    w.addStep(steps.get(Steps.Extract_Conditions)); // extract conditions
    setInheritExtracts();
  }

  private void resetNextSteps() {
    Notification n =
        new Notification(
            "Steps updated",
            "One of your choices has added or removed steps from the wizard. You can see your progress at the top.");
    n.setStyleName(ValoTheme.NOTIFICATION_CLOSABLE);
    n.setDelayMsec(-1);
    // n.show(UI.getCurrent().getPage()); TODO ability to select not to show them - addon notifique?
    List<WizardStep> steps = w.getSteps();
    List<WizardStep> copy = new ArrayList<WizardStep>();
    copy.addAll(steps);
    boolean isNew = false;
    for (int i = 0; i < copy.size(); i++) {
      WizardStep cur = copy.get(i);
      if (isNew) {
        w.removeStep(cur);
      }
      if (w.isActive(cur))
        isNew = true;
    }
  }

  /**
   * Test is a project has biological entities registered. Used to know availability of context
   * options
   * 
   * @param spaceCode Code of the selected openBIS space
   * @param code Code of the project
   * @return
   */
  public boolean projectHasBioEntities(String spaceCode, String code) {
    if (!openbis.projectExists(spaceCode, code))
      return false;
    for (Experiment e : openbis.getExperimentsOfProjectByCode(code)) {
      if (e.getExperimentTypeCode().equals("Q_EXPERIMENTAL_DESIGN"))
        return openbis.getSamplesofExperiment(e.getIdentifier()).size() > 0;
    }
    return false;
  }

  /**
   * Test is a project has biological extracts registered. Used to know availability of context
   * options
   * 
   * @param spaceCode Code of the selected openBIS space
   * @param code Code of the project
   * @return
   */
  public boolean projectHasExtracts(String spaceCode, String code) {
    if (!openbis.projectExists(spaceCode, code))
      return false;
    for (Experiment e : openbis.getExperimentsOfProjectByCode(code)) {
      if (e.getExperimentTypeCode().equals("Q_SAMPLE_EXTRACTION"))
        if (openbis.getSamplesofExperiment(e.getIdentifier()).size() > 0)
          return true;
    }
    return false;
  }

  public Wizard getWizard() {
    return w;
  }

  private String generateProjectCode() {
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

  public static enum Steps {
    Project_Context, Entities, Entity_Conditions, Entity_Tailoring, Extraction, Extract_Conditions, Extract_Tailoring, Extract_Pooling, Test_Samples, Test_Sample_Pooling, Registration;
  }

  /**
   * Initialize all possible steps in the wizard and the listeners used
   */
  public void init() {
    this.w = new Wizard();
    w.getFinishButton().setVisible(false);
    w.getFinishButton().setStyleName(ValoTheme.BUTTON_DANGER);
    w.getCancelButton().setStyleName(ValoTheme.BUTTON_DANGER);

    final ProjectSelectionComponent projSelection = new ProjectSelectionComponent();
    final ProjectContextStep contextStep = new ProjectContextStep(spaces, projSelection);
    final EntityStep entStep = new EntityStep(taxMap);
    final ConditionInstanceStep entCondInstStep =
        new ConditionInstanceStep(taxMap.keySet(), "Species", "Biol. Variables");
    final TailoringStep negStep1 = new TailoringStep("Biological Entities", false);
    final ExtractionStep extrStep = new ExtractionStep(tissueMap);
    final ConditionInstanceStep extrCondInstStep =
        new ConditionInstanceStep(tissueMap.keySet(), "Tissues", "Extr. Variables");
    final TailoringStep negStep2 = new TailoringStep("Sample Extracts", true);
    final TestStep techStep = new TestStep(measureTypes);
    final SummaryRegisterStep regStep = new SummaryRegisterStep(w);
    final PoolingStep poolStep1 = new PoolingStep(Steps.Extract_Pooling);
    final PoolingStep poolStep2 = new PoolingStep(Steps.Test_Sample_Pooling);

    steps = new HashMap<Steps, WizardStep>();
    steps.put(Steps.Project_Context, contextStep);
    steps.put(Steps.Entities, entStep);
    steps.put(Steps.Entity_Conditions, entCondInstStep);
    steps.put(Steps.Entity_Tailoring, negStep1);
    steps.put(Steps.Extraction, extrStep);
    steps.put(Steps.Extract_Conditions, extrCondInstStep);
    steps.put(Steps.Extract_Tailoring, negStep2);
    steps.put(Steps.Extract_Pooling, poolStep1);
    steps.put(Steps.Test_Samples, techStep);
    steps.put(Steps.Test_Sample_Pooling, poolStep2);
    steps.put(Steps.Registration, regStep);

    this.dataAggregator = new WizardDataAggregator(steps, openbis, taxMap, tissueMap);
    w.addStep(contextStep);

    FocusListener fListener = new FocusListener() {
      private static final long serialVersionUID = 8721337946386845992L;

      @Override
      public void focus(FocusEvent event) {
        TextField p = projSelection.getProjectField();
        if (!p.isValid() || p.isEmpty()) {
          projSelection.tryEnableCustomProject(generateProjectCode());
        }
        contextStep.makeContextVisible();
      }
    };
    projSelection.getProjectField().addFocusListener(fListener);

    Button.ClickListener projCL = new Button.ClickListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -6646294420820222646L;

      @Override
      public void buttonClick(ClickEvent event) {
        projSelection.tryEnableCustomProject(generateProjectCode());
      }
    };
    projSelection.getProjectReloadButton().addClickListener(projCL);

    Button.ClickListener cl = new Button.ClickListener() {
      /**
       * 
       */
      private static final long serialVersionUID = -8427457552926464653L;

      @Override
      public void buttonClick(ClickEvent event) {
        String src = event.getButton().getCaption();
        if (src.equals("Register All")) {
          regStep.getRegisterButton().setEnabled(false);
          ProjectContextStep context = (ProjectContextStep) steps.get(Steps.Project_Context);
          String desc = context.getDescription();
          String projInfo = context.getProjectSecondaryName();
          openbisCreator.registerProjectWithExperimentsAndSamplesBatchWise(regStep.getSamples(),
              desc, projInfo, regStep.getProgressBar(), regStep.getProgressLabel(),
              new RegisteredSamplesReadyRunnable(regStep));
        }
      }

      /**
       * Creates Factor instances from the XML Factor and Gene Loci columns of the TSV and creates
       * and XML from them that can be registered to the openBIS model
       * 
       * @param metadata The metadata map of a sample
       */
      // private void fixXMLProps(Map<String, String> metadata) {
      // XMLParser p = new XMLParser();
      // LociParser lp = new LociParser();
      // List<Factor> factors = new ArrayList<Factor>();
      // if (metadata.get("XML_FACTORS") != null) {
      // String[] fStrings = metadata.get("XML_FACTORS").split(";");
      // for (String factor : fStrings) {
      // if (factor.length() > 1) {
      // String[] fields = factor.split(":");
      // for (int i = 0; i < fields.length; i++)
      // fields[i] = fields[i].trim();
      // String lab = fields[0].replace(" ", "");
      // String val = fields[1];
      // if (fields.length > 2)
      // factors.add(new Factor(lab, val, fields[2]));
      // else
      // factors.add(new Factor(lab, val));
      // }
      // }
      // try {
      // metadata.put("Q_PROPERTIES", p.toString(p.createXMLFromFactors(factors)));
      // } catch (JAXBException e) {
      // e.printStackTrace();
      // }
      // }
      // metadata.remove("XML_FACTORS");
      //
      // List<GeneLocus> loci = new ArrayList<GeneLocus>();
      // if (metadata.get("XML_LOCI") != null) {
      // String[] lStrings = metadata.get("XML_LOCI").split(";");
      // for (String locus : lStrings) {
      // if (locus.length() > 1) {
      // String[] fields = locus.split(":");
      // for (int i = 0; i < fields.length; i++)
      // fields[i] = fields[i].trim();
      // String lab = fields[0];
      // String[] alleles = fields[1].split("/");
      // loci.add(new GeneLocus(lab, new ArrayList<String>(Arrays.asList(alleles))));
      // }
      // }
      // try {
      // metadata.put("Q_LOCI", lp.toString(lp.createXMLFromLoci(loci)));
      // } catch (JAXBException e) {
      // e.printStackTrace();
      // }
      // }
      // metadata.remove("XML_LOCI");
      // }
    };
    regStep.getDownloadButton().addClickListener(cl);
    regStep.getRegisterButton().addClickListener(cl);

    /**
     * Space selection listener
     */
    ValueChangeListener spaceSelectListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -7487587994432604593L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        ComboBox box = contextStep.getSpaceBox();
        box.removeStyleName(ValoTheme.LABEL_SUCCESS);
        contextStep.resetProjects();
        String space = contextStep.getSpaceCode();
        if (space != null) {
          box.addStyleName(ValoTheme.LABEL_SUCCESS);
          List<String> projects = new ArrayList<String>();
          for (Project p : openbis.getProjectsOfSpace(space)) {
            projects.add(p.getCode());
          }
          contextStep.setProjectCodes(projects);
          contextStep.enableNewContextOption(true);
        }
      }

    };
    contextStep.getSpaceBox().addValueChangeListener(spaceSelectListener);

    /**
     * Project selection listener
     */

    ValueChangeListener projectSelectListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = -443162343850159312L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        contextStep.tryEnableCustomProject(generateProjectCode());
        contextStep.resetExperiments();
        String space = contextStep.getSpaceCode();
        String project = contextStep.getProjectCode();
        boolean hasBioEntities = projectHasBioEntities(space, project);
        boolean hasExtracts = projectHasExtracts(space, project);
        contextStep.enableExtractContextOption(hasBioEntities);
        contextStep.enableMeasureContextOption(hasExtracts);
        // contextStep.enableCopyContextOption(projectHasBioEntities(space, project) ||
        // hasExtracts);
        contextStep.enableTSVWriteContextOption(hasBioEntities);
        if (project != null && !project.isEmpty()) {
          contextStep.makeContextVisible();
          List<ExperimentBean> beans = new ArrayList<ExperimentBean>();
          for (Experiment e : openbis.getExperimentsOfProjectByCode(project)) {
            if (designExperimentTypes.contains(e.getExperimentTypeCode())) {
              int numOfSamples = openbis.getSamplesofExperiment(e.getIdentifier()).size();
              beans.add(new ExperimentBean(e.getIdentifier(), e.getExperimentTypeCode(), Integer
                  .toString(numOfSamples)));
            }
          }
          contextStep.setExperiments(beans);
        }
      }

    };
    contextStep.getProjectBox().addValueChangeListener(projectSelectListener);

    /**
     * Experiment selection listener
     */

    ValueChangeListener expSelectListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 1931780520075315462L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        contextStep.resetSamples();
        ExperimentBean exp = contextStep.getExperimentName();
        if (exp != null) {
          List<NewSampleModelBean> beans = new ArrayList<NewSampleModelBean>();
          for (Sample s : openbis.getSamplesofExperiment(exp.getID())) {
            beans.add(new NewSampleModelBean(s.getCode(),
                s.getProperties().get("Q_SECONDARY_NAME"), s.getSampleTypeCode()));
          }
          contextStep.setSamples(beans);
        }
      }

    };
    contextStep.getExperimentTable().addValueChangeListener(expSelectListener);

    /**
     * Project context (radio buttons) listener
     */

    ValueChangeListener projectContextListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 5972535836592118817L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        if (contextStep.getProjectContext().getValue() != null) {
          resetNextSteps();
          OptionGroup projectContext = contextStep.getProjectContext();
          List<String> contextOptions = contextStep.getContextOptions();
          List<ExperimentBean> experiments = contextStep.getExperiments();
          String context = (String) projectContext.getValue();
          List<ExperimentBean> beans = new ArrayList<ExperimentBean>();
          // inherit from bio entities
          if (contextOptions.get(1).equals(context)) {
            for (ExperimentBean b : experiments) {
              if (b.getExperiment_type().equals(ExperimentType.Q_EXPERIMENTAL_DESIGN.toString()))
                beans.add(b);
            }
            setInheritEntities();
            dataAggregator.setInheritEntities(true);
            dataAggregator.setInheritExtracts(false);
          }
          // inherit from sample extraction
          if (contextOptions.get(2).equals(context)) {
            for (ExperimentBean b : experiments) {
              if (b.getExperiment_type().equals(ExperimentType.Q_SAMPLE_EXTRACTION.toString()))
                beans.add(b);
            }
            setInheritExtracts();
            dataAggregator.setInheritEntities(false);
            dataAggregator.setInheritExtracts(true);
          }
          // new experiments
          if (contextOptions.get(0).equals(context)) {
            setCreateEntities();
            dataAggregator.setInheritEntities(false);
            dataAggregator.setInheritExtracts(false);
            contextStep.hideExperiments();
          }
          // copy experiments
          // if (contextOptions.get(3).equals(context)) {
          // beans.addAll(experiments);
          // setUpLoadStep();
          // }
          // read only tsv creation
          if (contextOptions.get(3).equals(context)) {
            beans.addAll(experiments);
            setUpLoadStep();
          }
          if (beans.size() > 0)
            contextStep.showExperiments(beans);
        }
      }
    };
    contextStep.getProjectContext().addValueChangeListener(projectContextListener);

    /**
     * Listeners for pooling samples
     */
    ValueChangeListener poolingListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 2393762547426343668L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        resetNextSteps();
        if (negStep2.pool()) {
          setExtractsPooling();
        } else {
          setTestStep();
        }
      }
    };
    negStep2.getPoolBox().addValueChangeListener(poolingListener);

    ValueChangeListener testPoolListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 2393762547426343668L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        resetNextSteps();
        if (techStep.hasPools()) {
          setTestsPooling();
        } else {
          setUpLoadStep();
        }
      }
    };
    techStep.setPoolListener(testPoolListener);


    ValueChangeListener noMeasureListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 2393762547426343668L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        techStep.changeTechPanel();
      }
    };
    techStep.getNotMeasured().addValueChangeListener(noMeasureListener);

    /**
     * Listeners for entity and extract conditions
     */
    ValueChangeListener entityConditionSetListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 2393762547426343668L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        resetNextSteps();
        if (entStep.isConditionsSet().getValue() != null) {
          setEntityConditions();
        } else {
          setInheritEntities();
        }
      }
    };
    entStep.isConditionsSet().addValueChangeListener(entityConditionSetListener);

    ValueChangeListener extractConditionSetListener = new ValueChangeListener() {

      /**
       * 
       */
      private static final long serialVersionUID = 4879458823482873630L;

      @Override
      public void valueChange(ValueChangeEvent event) {
        resetNextSteps();
        if (extrStep.conditionsSet().getValue() != null) {
          setExtractConditions();
        } else {
          setInheritExtracts();
        }
      }
    };
    extrStep.conditionsSet().addValueChangeListener(extractConditionSetListener);

    TextField f = contextStep.getProjectCodeField();
    CompositeValidator vd = new CompositeValidator();
    RegexpValidator p =
        new RegexpValidator("Q[A-Xa-x0-9]{4}",
            "Project must have length of 5, start with Q and not contain Y or Z");
    vd.addValidator(p);
    vd.addValidator(new ProjectNameValidator());
    f.addValidator(vd);
    f.setImmediate(true);
    f.setValidationVisible(true);

    WizardProgressListener wl = new WizardProgressListener() {
      @Override
      public void wizardCompleted(WizardCompletedEvent event) {}

      @Override
      public void wizardCancelled(WizardCancelledEvent event) {}

      @Override
      public void stepSetChanged(WizardStepSetChangedEvent event) {}

      /**
       * Reactions to step changes in the wizard
       */
      @Override
      public void activeStepChanged(WizardStepActivationEvent event) {
        // Context Step
        if (event.getActivatedStep().equals(contextStep)) {
          // contextStep.allowNext(false);
          regStep.enableDownloads(false);
        }
        // Entity Setup Step
        if (event.getActivatedStep().equals(entStep)) {
          bioFactorInstancesSet = false;
          // }
        }
        // Entity Condition Instances Step
        if (event.getActivatedStep().equals(entCondInstStep)) {
          reloadConditionsPreviewTable(entCondInstStep,
              Integer.toString(entStep.getBioRepAmount()), new ArrayList<AOpenbisSample>());
          if (!bioFactorInstancesSet) {
            if (entStep.speciesIsFactor())
              entCondInstStep.initOptionsFactorField(entStep.getSpeciesAmount());
            entCondInstStep.initFactorFields(entStep.getFactors());
            initConditionListener(entCondInstStep, Integer.toString(entStep.getBioRepAmount()),
                new ArrayList<AOpenbisSample>());
            bioFactorInstancesSet = true;
          }
        }
        // Negative Selection of Entities
        if (event.getActivatedStep().equals(negStep1)) {
          try {
            negStep1.setSamples(dataAggregator.prepareEntities(entCondInstStep.getPreSelection()));
          } catch (JAXBException e) {
            e.printStackTrace();
          }
        }
        // Extract Setup Step
        if (event.getActivatedStep().equals(extrStep)) {
          dataAggregator.setEntities(negStep1.getSamples());
          extractFactorInstancesSet = false;
        }
        // Extract Factor Instances Step
        if (event.getActivatedStep().equals(extrCondInstStep)) {
          reloadConditionsPreviewTable(extrCondInstStep,
              Integer.toString(extrStep.getExtractRepAmount()), dataAggregator.getEntities());
          if (!extractFactorInstancesSet) {
            if (extrStep.tissueIsFactor())
              extrCondInstStep.initOptionsFactorField(extrStep.getTissueAmount());
            extrCondInstStep.initFactorFields(extrStep.getFactors());
            initConditionListener(extrCondInstStep,
                Integer.toString(extrStep.getExtractRepAmount()), dataAggregator.getEntities());
            extractFactorInstancesSet = true;
          }
        }
        // Negative Selection of Extracts
        if (event.getActivatedStep().equals(negStep2)) {
          extractPoolsSet = false;
          try {
            negStep2.setSamples(dataAggregator.prepareExtracts(extrCondInstStep.getPreSelection()));
          } catch (JAXBException e) {
            e.printStackTrace();
          }
        }
        // Extract Pool Step
        if (event.getActivatedStep().equals(poolStep1)) {
          dataAggregator.resetExtracts();
          if (!extractPoolsSet) {
            poolStep1.setSamples(
                new ArrayList<List<AOpenbisSample>>(Arrays.asList(negStep2.getSamples())),
                Steps.Extract_Pooling);
            extractPoolsSet = true;
          }
        }
        // Test Setup Step
        if (event.getActivatedStep().equals(techStep)) {
          testPoolsSet = false;
          List<AOpenbisSample> all = new ArrayList<AOpenbisSample>();
          all.addAll(negStep2.getSamples());
          all.addAll(dataAggregator.createPoolingSamples(poolStep1.getPools()));
          dataAggregator.setExtracts(all);
        }
        // Test Pool Step
        if (event.getActivatedStep().equals(poolStep2)) {
          dataAggregator.resetTests();
          if (!testPoolsSet) {
            poolStep2.setSamples(dataAggregator.prepareTestSamples(), Steps.Test_Sample_Pooling);
            testPoolsSet = true;
          }
        }
        // TSV and Registration Step
        if (event.getActivatedStep().equals(regStep)) {
          regStep.enableDownloads(false);
          // Test samples were filled out
          if (w.getSteps().contains(steps.get(Steps.Test_Samples))) {
            if (!testPoolsSet)
              dataAggregator.prepareTestSamples();
            List<AOpenbisSample> all = new ArrayList<AOpenbisSample>();
            all.addAll(dataAggregator.getTests());
            all.addAll(dataAggregator.createPoolingSamples(poolStep2.getPools()));
            dataAggregator.setTests(all);
            createTSV();
            try {
              prep.processTSV(dataAggregator.getTSV(), false);
            } catch (IOException e) {
              e.printStackTrace();
            }
            armDownloadButtons(regStep.getDownloadButton(), regStep.getGraphButton());
            regStep.setSummary(prep.getSummary());
            regStep.setProcessed(prep.getProcessed());
          }
          if (regStep.summaryIsSet()) {
            regStep.setRegEnabled(true);
          }
          // Copy mode
          if (contextStep.copyModeSet()) {
            try {
              dataAggregator.copyExperiment();
            } catch (JAXBException e1) {
              e1.printStackTrace();
            }
            createTSV();
            try {
              prep.processTSV(dataAggregator.getTSV(), false);
            } catch (IOException e) {
              e.printStackTrace();
            }
            armDownloadButtons(regStep.getDownloadButton(), regStep.getGraphButton());
            regStep.setSummary(prep.getSummary());
            regStep.setProcessed(prep.getProcessed());
          }
          // Write TSV mode
          if (contextStep.fetchTSVModeSet()) {
            try {
              dataAggregator.parseAll();
            } catch (JAXBException e1) {
              e1.printStackTrace();
            }
            createTSV();
            try {
              prep.processTSV(dataAggregator.getTSV(), false);
            } catch (IOException e) {
              e.printStackTrace();
            }
            armDownloadButtons(regStep.getDownloadButton(), regStep.getGraphButton());
            regStep.setSummary(prep.getSummary());
            regStep.setProcessed(prep.getProcessed());
          }
        }
      }
    };
    w.addListener(wl);
  }

  protected void createTSV() {
    try {
      dataAggregator.createTSV();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  protected void initConditionListener(final ConditionInstanceStep step, final String amount,
      final List<AOpenbisSample> previousLevel) {

    ValueChangeListener listener = new ValueChangeListener() {
      /**
         * 
         */
      private static final long serialVersionUID = 7925081983580407077L;

      public void valueChange(ValueChangeEvent event) {
        reloadConditionsPreviewTable(step, amount, previousLevel);
      }
    };
    step.attachListener(listener);
  }

  protected void reloadConditionsPreviewTable(ConditionInstanceStep step, String amount,
      List<AOpenbisSample> previousLevel) {
    if (step.validInput()) {
      if (previousLevel.isEmpty())
        step.buildTable(preparePreviewPermutations(step.getFactors()), amount);
      else
        step.buildTable(preparePreviewPermutations(step.getFactors(), previousLevel), amount);
    } else {
      step.destroyTable();
    }

  }

  /**
   * Prepare all condition permutations for the user to set the amounts when conditions from a
   * previous tier are included
   * 
   * @param factorLists
   * @param previousTier Samples of the previous tier
   * @return
   */
  public List<String> preparePreviewPermutations(List<List<Factor>> factorLists,
      List<AOpenbisSample> previousTier) {
    List<String> permutations = new ArrayList<String>();
    for (AOpenbisSample e : previousTier) {
      List<List<String>> res = new ArrayList<List<String>>();
      String secName = e.getQ_SECONDARY_NAME();
      if (secName == null)
        secName = "";
      String condKey = "(" + e.getCode().split("-")[1] + ") " + secName;
      res.add(new ArrayList<String>(Arrays.asList(condKey)));
      for (List<Factor> instances : factorLists) {
        List<String> factorValues = new ArrayList<String>();
        for (Factor f : instances) {
          String name = f.getValue() + f.getUnit();
          factorValues.add(name);
        }
        res.add(factorValues);
      }
      permutations.addAll(dataAggregator.generatePermutations(res));
    }
    return permutations;
  }

  /**
   * Prepare all condition permutations for the user to set the amounts
   * 
   * @param factorLists
   * @return
   */
  public List<String> preparePreviewPermutations(List<List<Factor>> factorLists) {
    List<List<String>> res = new ArrayList<List<String>>();
    for (List<Factor> instances : factorLists) {
      List<String> factorValues = new ArrayList<String>();
      for (Factor f : instances) {
        String name = f.getValue() + f.getUnit();
        factorValues.add(name);
      }
      res.add(factorValues);
    }
    List<String> permutations = dataAggregator.generatePermutations(res);
    return permutations;
  }

  protected void armDownloadButtons(Button tsv, Button graph) {
    StreamResource tsvStream =
        getTSVStream(dataAggregator.getTSVContent(), dataAggregator.getTSVName());
    if (tsvDL == null) {
      tsvDL = new FileDownloader(tsvStream);
      tsvDL.extend(tsv);
    } else
      tsvDL.setFileDownloadResource(tsvStream);
    StreamResource graphStream = getGraphStream(prep.toGraphML(), "test");
    if (graphDL == null) {
      graphDL = new FileDownloader(graphStream);
      graphDL.extend(graph);
    } else
      graphDL.setFileDownloadResource(graphStream);
  }

  public StreamResource getGraphStream(final String content, String name) {
    StreamResource resource = new StreamResource(new StreamResource.StreamSource() {
      @Override
      public InputStream getStream() {
        try {
          InputStream is = new ByteArrayInputStream(content.getBytes());
          return is;
        } catch (Exception e) {
          e.printStackTrace();
          return null;
        }
      }
    }, String.format("%s.graphml", name));
    return resource;
  }

  public StreamResource getTSVStream(final String content, String name) {
    StreamResource resource = new StreamResource(new StreamResource.StreamSource() {
      @Override
      public InputStream getStream() {
        try {
          InputStream is = new ByteArrayInputStream(content.getBytes());
          return is;
        } catch (Exception e) {
          e.printStackTrace();
          return null;
        }
      }
    }, String.format("%s.tsv", name));
    return resource;
  }

}
