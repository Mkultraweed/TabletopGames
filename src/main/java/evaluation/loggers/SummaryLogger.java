package evaluation.loggers;

import core.interfaces.IStatisticLogger;
import evaluation.summarisers.TAGNumericStatSummary;
import utilities.Pair;
import evaluation.summarisers.TAGOccurrenceStatSummary;
import evaluation.summarisers.TAGStatSummary;

import java.io.*;
import java.util.*;

import static java.util.stream.Collectors.*;

/**
 * Statistics Logger that just takes in Numeric data and maintains summary statistics for each type:
 * - mean, min, max, standard error, standard deviation, n
 * A summary is printed out in alphabetic order once finished.
 */
public class SummaryLogger implements IStatisticLogger {

    File logFile;
    public boolean printToConsole = true;
    Map<String, TAGStatSummary> data = new LinkedHashMap<>();

    public SummaryLogger() {}

    public SummaryLogger(String logFile) {
        this.logFile = new File(logFile);
    }

    @Override
    public void record(String key, Object value) {
        TAGStatSummary summary = data.get(key);
        if (value instanceof Number) {
            // A number, record details numeric statistics
            if (!data.containsKey(key)) {
                summary = new TAGNumericStatSummary(key);
                data.put(key, summary);
            }
            ((TAGNumericStatSummary)summary).add((Number) value);
        } else {
            if (value instanceof Map && ((Map<?, ?>) value).keySet().iterator().next() instanceof String) {
                // A collection of other stats that should be recorded separately, ignore key // TODO: maybe we want to keep the key too in the name of records?
                record((Map<String, ?>) value);
            } else {
                // Some other kind of object, record occurrences
                if (!data.containsKey(key)) {
                    summary = new TAGOccurrenceStatSummary(key);
                    data.put(key, summary);
                }
                ((TAGOccurrenceStatSummary) summary).add(value);
            }
        }
    }

    /**
     * Any data that is not numeric will be silently ignored
     *
     * @param data A map of name -> Number pairs
     */
    @Override
    public void record(Map<String, ?> data) {
        for (String key : data.keySet()) {
            record(key, data.get(key));
        }
    }

    @Override
    public Map<String, TAGStatSummary> summary() {
        return data;
    }

    @Override
    public SummaryLogger emptyCopy(String id) {
        if (logFile == null) return new SummaryLogger();
        return new SummaryLogger(logFile.getPath()); // TODO include id in filename
    }

    @Override
    public void processDataAndFinish() {
        if (printToConsole)
            System.out.println(this);

        if (logFile != null) {

            try {
                if(logFile.exists())
                {
                    Pair<String, String> data = getFileOutput();
                    FileWriter writer = new FileWriter(logFile, true);
                    writer.write(data.a); //header
                    writer.write(data.b); //body
                    writer.flush();
                    writer.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public Pair<String, String> getFileOutput()
    {
        // We now write this to the file
        StringBuilder header = new StringBuilder();
        StringBuilder outputData = new StringBuilder();
        for (String key : data.keySet()) {
            TAGStatSummary summary = data.get(key);
            if (summary instanceof TAGOccurrenceStatSummary) {
                header.append(key).append("\t");
                outputData.append(data.get(key)).append("\t");
            } else if (summary instanceof TAGNumericStatSummary) {
                TAGNumericStatSummary statSummary = (TAGNumericStatSummary) summary;
                if (data.get(key).n() == 1) {
                    header.append(key).append("\t");
                    outputData.append(String.format("%.3g\t", statSummary.mean()));
                } else {
                    header.append(key).append("\t").append(key).append("_se\t");
                    outputData.append(String.format("%.3g\t%.2g\t", statSummary.mean(), statSummary.stdErr()));
                    header.append(key).append("_sd\t");
                    outputData.append(String.format("%.3g\t", statSummary.sd()));
                    header.append(key).append("_median\t");
                    outputData.append(String.format("%.3g\t", statSummary.median()));
                    header.append(key).append("_min\t");
                    outputData.append(String.format("%.3g\t", statSummary.min()));
                    header.append(key).append("_max\t");
                    outputData.append(String.format("%.3g\t", statSummary.max()));
                    header.append(key).append("_skew\t");
                    outputData.append(String.format("%.3g\t", statSummary.skew()));
                    header.append(key).append("_kurtosis\t");
                    outputData.append(String.format("%.3g\t", statSummary.kurtosis()));
                }
            }
        }
        header.append("\n");
        outputData.append("\n");
        return new Pair<>(header.toString(), outputData.toString());
    }

    @Override
    public void processDataAndNotFinish() {
        // do nothing until we have full finished
    }


    @Override
    public String toString() {
        // We want to print out something vaguely pretty

        List<String> alphabeticOrder = data.keySet().stream().sorted().collect(toList());
        StringBuilder sb = new StringBuilder();
        for (String key : alphabeticOrder) {
            TAGStatSummary summary = data.get(key);
            if (summary instanceof TAGNumericStatSummary) {
                // Print numeric data, stat summaries
                TAGNumericStatSummary stats = (TAGNumericStatSummary) summary;
                if (stats.n() == 1) {
                    sb.append(String.format("%30s  %8.3g\n", key, stats.mean()));
                } else {
                    sb.append(String.format("%30s  Mean: %8.3g +/- %6.2g,\tMedian: %8.3g,\tRange: [%3d, %3d],\tpop sd %8.3g,\tskew %8.3g,\tkurtosis %8.3g,\tn=%d\n", key,
                            stats.mean(), stats.stdErr(), stats.median(), (int) stats.min(), (int) stats.max(), stats.sd(), stats.skew(), stats.kurtosis(), stats.n()));
                }
            } else {
                // Print other data, each item toString + percentage of times it was that value
                TAGOccurrenceStatSummary stats = (TAGOccurrenceStatSummary) summary;
                sb.append(String.format("%30s  %30s\n", key, stats.shortString()));
            }
        }

        return sb.toString();
    }
}
