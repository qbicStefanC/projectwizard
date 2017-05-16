package registration;

import java.util.List;
import java.util.Map;

import model.ISampleBean;

public class RegisterableExperiment {

  String code;
  String type;
  List<ISampleBean> samples;
  Map<String, Object> properties;

  public RegisterableExperiment(String code, String type, List<ISampleBean> samples,
      Map<String, Object> properties) {
    this.code = code;
    this.type = type;
    this.samples = samples;
    this.properties = properties;
  }

  public String getType() {
    return type;
  }

  public String getCode() {
    return code;
  }

  public List<ISampleBean> getSamples() {
    return samples;
  }

  public Map<String, Object> getProperties() {
    return properties;
  }

  public void addSample(ISampleBean s) {
    samples.add(s);
  }
}
