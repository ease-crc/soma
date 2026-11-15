package main.reasoning;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;

/** Subprocess stdout is strictly protocol v1; semantic failures still have process exit 0. */
public final class HermitWorker {
    private HermitWorker() {}

    public static void main(String[] args) throws Exception {
        var report = HermitEvaluator.evaluate(Path.of(args[0]), args.length == 2 ? Path.of(args[1]) : null);
        System.out.print(new ObjectMapper().writeValueAsString(protocol(report)));
    }

    static LinkedHashMap<String, Object> protocol(ReasoningReport report) {
        var fields = new LinkedHashMap<String, Object>();
        fields.put("schemaVersion", 1);
        fields.put("complete", report.complete());
        fields.put("consistent", report.consistent());
        fields.put("toolErrors", report.toolErrors());
        fields.put("unsatisfiableClasses", report.unsatisfiableClasses());
        fields.put("emptyObjectProperties", report.emptyObjectProperties());
        fields.put("emptyDataProperties", report.emptyDataProperties());
        fields.put("qualityFindings", report.qualityFindings());
        return fields;
    }
}
