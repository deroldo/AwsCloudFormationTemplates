package br.com.deroldo.aws.cloudformation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

public class Main {

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try {

            final ObjectReader r = mapper.readerFor(Map.class);

            String path = Main.class.getClassLoader().getResource("teste.yml").getPath();
            Map<String, Map> userMap = r.readValue(new File(path));

            Map<String, Map> result = new HashMap<>();
            result.put("Resources", new HashMap());
            result.put("Outputs", new HashMap());
            result.put("Mappings", new HashMap());
            int[] aux = {1};
            userMap.keySet().forEach(resourceName -> {
                String templateName = Main.class.getClassLoader().getResource(resourceName + ".yml").getPath();
                try {

                    String[] templateYml = {Files.readAllLines(Paths.get(templateName)).stream()
                            .reduce((s1, s2) -> s1.concat("\n").concat(s2)).get()};

                    Map<String, Object> toReplace = new HashMap<>();

                    Map<String, Object> userParamMap = userMap.get(resourceName);
                    userParamMap.keySet().forEach(resourceParamName -> {
                        if (userParamMap.get(resourceParamName) instanceof List){
                            final String listValues = ((List<Map>) userParamMap.get(resourceParamName)).stream()
                                    .map(l -> "- " + l.keySet().iterator().next().toString() + ": " + l.values().iterator()
                                            .next().toString())
                                    .reduce((s1, s2) -> s1.concat("\n").concat(s2))
                                    .get();

                            final String key = UUID.randomUUID().toString();
                            toReplace.put(key, listValues);

                            templateYml[0] = templateYml[0].replace("Ref: " + resourceParamName, key);
                        } else {
                            templateYml[0] = templateYml[0].replace("Ref: " + resourceParamName, userParamMap.get(resourceParamName).toString());
                        }
                    });

                    Map<String, Map> templateMap = r.readValue(new File(templateName));
                    Map<String, Object> templateDefaultParams = templateMap.get("Parameters");
                    templateDefaultParams.keySet().forEach(templateParamName -> {
                        Map<String, Object> param = (Map<String, Object>) templateDefaultParams.get(templateParamName);
                        if (Objects.nonNull(param.get("Default"))){
                            templateYml[0] = templateYml[0].replace("Ref: " + templateParamName, param.get("Default").toString());
                        }
                    });

                    toReplace.forEach((k, v) -> {
                        final int index = templateYml[0].indexOf(k);
                        final int enterIndex = templateYml[0].substring(0, index).lastIndexOf("\n");
                        final int diff = templateYml[0].substring(enterIndex, index).replace(" ", "").length();
                        final int qdtEspaco = index - enterIndex - diff;

                        String espacos = "";
                        for (int i = 0; i < qdtEspaco; i++){
                            espacos += " ";
                        }

                        templateYml[0] = templateYml[0].replace(k, v.toString().replace("\n", "\n" + espacos));
                    });
                    templateMap = r.readValue(templateYml[0]);

                    final Map<String, Map> finalTemplateMap = new HashMap<>();
                    templateMap.get("Resources").forEach((k, v) -> {
                        finalTemplateMap.put(k.toString()+aux[0], (Map) v);
                    });

                    result.get("Resources").putAll(finalTemplateMap);

                    if (templateMap.get("Outputs") != null){
                        result.get("Outputs").putAll(templateMap.get("Outputs"));
                    }
                    if (templateMap.get("Mappings") != null){
                        result.get("Mappings").putAll(templateMap.get("Mappings"));
                    }

                    aux[0] = aux[0] +1;

                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            if (result.get("Mappings").isEmpty()){
                result.remove("Mappings");
            }

            if (result.get("Outputs").isEmpty()){
                result.remove("Outputs");
            }

            System.out.println(mapper.writeValueAsString(result));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}