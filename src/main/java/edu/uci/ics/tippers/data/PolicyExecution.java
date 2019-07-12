package edu.uci.ics.tippers.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.tippers.common.PolicyConstants;
import edu.uci.ics.tippers.db.MySQLQueryManager;
import edu.uci.ics.tippers.db.MySQLResult;
import edu.uci.ics.tippers.fileop.Reader;
import edu.uci.ics.tippers.fileop.Writer;
import edu.uci.ics.tippers.model.guard.FactorExtension;
import edu.uci.ics.tippers.model.guard.FactorSearch;
import edu.uci.ics.tippers.model.guard.GuardHit;
import edu.uci.ics.tippers.model.guard.PredicateMerge;
import edu.uci.ics.tippers.model.policy.BEExpression;
import edu.uci.ics.tippers.model.policy.BEPolicy;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Created by cygnus on 12/12/17.
 */
public class PolicyExecution {
    private PolicyGeneration policyGen;

    private Writer writer;
    private MySQLQueryManager mySQLQueryManager;

    private static boolean BASE_LINE;
    private static boolean RESULT_CHECK;

    private static boolean EXTEND_PREDICATES;
    private static int SEARCH_DEPTH;

    private static boolean GUARD_UNION;
    private static int NUM_OF_REPS;
    private static long QUERY_TIMEOUT;

    private static String RESULTS_FILE;


    public PolicyExecution() {

        try {
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("config/basic.properties");
            Properties props = new Properties();
            if (inputStream != null) {
                props.load(inputStream);
                BASE_LINE = Boolean.parseBoolean(props.getProperty("baseline"));
                RESULT_CHECK = Boolean.parseBoolean(props.getProperty("resultCheck"));
                EXTEND_PREDICATES = Boolean.parseBoolean(props.getProperty("factor_extension"));
                SEARCH_DEPTH = Integer.parseInt(props.getProperty("search_depth"));
                GUARD_UNION = Boolean.parseBoolean(props.getProperty("union"));
                NUM_OF_REPS = Integer.parseInt(props.getProperty("numberOfRepetitions"));
                QUERY_TIMEOUT = Long.parseLong(props.getProperty("timeout"));
                RESULTS_FILE = props.getProperty("results_file");
            }

        } catch (IOException ie) {
           ie.printStackTrace();
        }

        policyGen = new PolicyGeneration();
        writer = new Writer();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setDateFormat(sdf);
        mySQLQueryManager = new MySQLQueryManager(QUERY_TIMEOUT);
    }


    //TODO: Fix the result checker by passing the fileName to write to
    public void runBEPolicies(String policyDir) {

        //Filtering out only json files
        File dir = new File(policyDir);
        File[] policyFiles = dir.listFiles((dir1, name) -> name.toLowerCase().endsWith(".json"));
        Arrays.sort(policyFiles != null ? policyFiles : new File[0]);

        try {
            Files.delete(Paths.get(policyDir + RESULTS_FILE));
        } catch (IOException ioException) { }

        if (policyFiles != null) {
            for (File file : policyFiles) {
                TreeMap<String, String> policyRunTimes = new TreeMap<>();
                int numOfPolicies = Integer.parseInt(file.getName().replaceAll("\\D+",""));
                System.out.println(numOfPolicies + " being processed......");
                BEExpression beExpression = new BEExpression();
                beExpression.parseJSONList(Reader.readTxt(policyDir + file.getName()));
                beExpression.setEstCost();

                try {

                    MySQLResult tradResult = new MySQLResult();

                    if (BASE_LINE) {
                        tradResult = mySQLQueryManager.runTimedQueryWithRepetitions(beExpression.createQueryFromPolices(),
                                RESULT_CHECK, NUM_OF_REPS);
                        Duration runTime = Duration.ofMillis(0);
                        runTime = runTime.plus(tradResult.getTimeTaken());
                        policyRunTimes.put(file.getName(), String.valueOf(runTime.toMillis()));
                        System.out.println("Baseline approach for " + file.getName() + " policies took " + runTime.toMillis());
                    }

//                    FactorSearch fs = new FactorSearch(beExpression);
//                    fs.search(SEARCH_DEPTH);
//                    Instant fsEnd = Instant.now();
//                    guardGen = guardGen.plus(Duration.between(fsStart, fsEnd));
//                    policyRunTimes.put(file.getName() + "-guardGeneration", String.valueOf(guardGen.toMillis()));
//                    System.out.println("Guard Generation time: " + guardGen);
//                    writer.addGuardReport(fs.printDetailedResults(1), policyDir, RESULTS_FILE);

                    Duration guardGen = Duration.ofMillis(0);
                    Instant fsStart = Instant.now();
                    GuardHit gh = new GuardHit(beExpression, EXTEND_PREDICATES);
                    Instant fsEnd = Instant.now();
                    guardGen = guardGen.plus(Duration.between(fsStart, fsEnd));
                    policyRunTimes.put(file.getName() + "-guardGeneration", String.valueOf(guardGen.toMillis()));
                    System.out.println("Guard Generation time: " + guardGen + " Number of Guards: " + gh.numberOfGuards());

                    /** Result checking **/
                    if(RESULT_CHECK){
                        System.out.println("Verifying results......");
                        System.out.println("Guard query: " + gh.createGuardedQuery(false));
                        MySQLResult guardResult = mySQLQueryManager.runTimedSubQuery(gh.createGuardedQuery(false),
                                RESULT_CHECK);
                        Boolean resultSame = tradResult.checkResults(guardResult);
                        if(!resultSame){
                            System.out.println("*** Query results don't match with generated guard!!! Halting Execution ***");
                            policyRunTimes.put(file.getName() + "-results-incorrect", String.valueOf(PolicyConstants.MAX_DURATION.toMillis()));
                            return;
                        }
                    }
//
//                    Duration execTime = Duration.ofMillis(0);
//                    if(GUARD_UNION) {
//                        MySQLResult execResult = mySQLQueryManager.runTimedQueryExp(gh.createGuardedQuery(GUARD_UNION));
//                        execTime = execTime.plus(execResult.getTimeTaken());
//                        policyRunTimes.put(file.getName() + "-executionTime with UNION", String.valueOf(execTime.toMillis()));
//                        writer.appendToCSVReport(policyRunTimes, policyDir, RESULTS_FILE);
//                        policyRunTimes.clear();
//                    }
//                    else{
////                        List<String> guardResults = new ArrayList<>();
////                        List <String> guardList = gh.createGuardQueries();
////                        Duration totalEval = Duration.ofMillis(0);
////                        for (String kOb : guardList) {
////                            System.out.println("Executing Guard Expression: " + kOb);
////                            StringBuilder guardString = new StringBuilder();
////                            MySQLResult completeResult = mySQLQueryManager.runTimedQueryWithOutSorting(kOb);
////                            guardString.append(completeResult.getTimeTaken().toMillis());
////                            guardString.append(",");
////                            guardString.append(kOb);
////                            guardResults.add(guardString.toString());
////                            totalEval = totalEval.plus(completeResult.getTimeTaken());
////                        }
////                        System.out.println("Total Guard Evaluation time: " + totalEval);
////                        guardResults.add("Total Guard Evaluation time," + totalEval.toMillis());
//                         writer.addGuardReport(gh.guardAnalysis(NUM_OF_REPS), policyDir, RESULTS_FILE);
//                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    policyRunTimes.put(file.getName(), PolicyConstants.MAX_DURATION.toString());
                }
            }
        }
    }

    private void generatePolicies(String policyDir) {
        int[] policyNumbers = {100, 1000, 50000};
        int[] policyEpochs = {0};
        System.out.println("Generating Policies ..........");
        List<BEPolicy> bePolicies = new ArrayList<>();
        for (int i = 0; i < policyNumbers.length; i++) {
            List<String> attributes = new ArrayList<>();
            attributes.add(PolicyConstants.TIMESTAMP_ATTR);
            attributes.add(PolicyConstants.ENERGY_ATTR);
            attributes.add(PolicyConstants.TEMPERATURE_ATTR);
            attributes.add(PolicyConstants.LOCATIONID_ATTR);
            attributes.add(PolicyConstants.USERID_ATTR);
            attributes.add(PolicyConstants.ACTIVITY_ATTR);
            List<BEPolicy> genPolicy = policyGen.generateOverlappingPolicies(policyNumbers[i], 0.3, attributes, bePolicies);
            bePolicies.clear();
            bePolicies.addAll(genPolicy);
        }
    }

    private void persistPolicies(int numOfPolicies) {
        System.out.println("Generating Policies ..........");
        List<String> attributes = new ArrayList<>();
        attributes.add(PolicyConstants.TIMESTAMP_ATTR);
        attributes.add(PolicyConstants.ENERGY_ATTR);
        attributes.add(PolicyConstants.TEMPERATURE_ATTR);
        attributes.add(PolicyConstants.LOCATIONID_ATTR);
        attributes.add(PolicyConstants.USERID_ATTR);
        attributes.add(PolicyConstants.ACTIVITY_ATTR);
        policyGen.persistOverlappingPolicies(numOfPolicies, 0.3, attributes);
    }



    public static void main(String args[]) {
        PolicyExecution pe = new PolicyExecution();
//        pe.persistPolicies(100);
//        pe.generatePolicies(PolicyConstants.BE_POLICY_DIR);
        pe.runBEPolicies(PolicyConstants.BE_POLICY_DIR);
    }
}