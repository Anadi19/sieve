package edu.uci.ics.tippers.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.tippers.common.PolicyConstants;
import edu.uci.ics.tippers.common.PolicyEngineException;
import edu.uci.ics.tippers.db.MySQLConnectionManager;
import edu.uci.ics.tippers.db.MySQLQueryManager;
import edu.uci.ics.tippers.fileop.Reader;
import edu.uci.ics.tippers.fileop.Writer;
import edu.uci.ics.tippers.model.guard.PredicateExtension;
import edu.uci.ics.tippers.model.guard.FactorSelection;
import edu.uci.ics.tippers.model.policy.BEExpression;
import edu.uci.ics.tippers.model.policy.BEPolicy;
import edu.uci.ics.tippers.model.policy.ObjectCondition;
import edu.uci.ics.tippers.model.query.BasicQuery;
import edu.uci.ics.tippers.model.query.RangeQuery;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Created by cygnus on 12/12/17.
 * Heavily borrowed from Benchmark code
 */
public class PolicyExecution {

    private long timeout = 250000;

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Connection connection;

    private static final int[] policyNumbers = {10, 20, 30, 40, 50};

    private static PolicyGeneration policyGen;

    private final ObjectMapper objectMapper = new ObjectMapper();

    Writer writer;

    MySQLQueryManager mySQLQueryManager;


    public PolicyExecution(){
        this.connection = MySQLConnectionManager.getInstance().getConnection();
        policyGen = new PolicyGeneration();
        writer = new Writer();
        objectMapper.setDateFormat(sdf);
        mySQLQueryManager = new MySQLQueryManager();
    }

    private List<BasicQuery> readBasicPolicy(String fileName){
        String values = Reader.readTxt(fileName);
        List<BasicQuery> basicQueries = new ArrayList<BasicQuery>();
        try {
            basicQueries.addAll(objectMapper.readValue(values,
                    new TypeReference<List<BasicQuery>>() {
                    }));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return basicQueries;
    }

    public Duration runBasicQuery(List<BasicQuery> basicQueries){
        String query = "SELECT * FROM SEMANTIC_OBSERVATION " +
                "WHERE " + IntStream.range(0, basicQueries.size()-1 ).mapToObj(i-> basicQueries.get(i).createPredicate())
                .collect(Collectors.joining(" OR "));
        try {
            return mySQLQueryManager.runWithThread(query, "bq");
        } catch (Exception e) {
            e.printStackTrace();
            throw new PolicyEngineException("Error Running Query");
        }
    }

    public Map<String, Duration> runBasicQueries(String policyDir) {

        Map<String, Duration> policyRunTimes = new HashMap<>();

        File[] policyFiles = new File(policyDir).listFiles();

        for (File file : policyFiles) {

            List<BasicQuery> basicQueries = readBasicPolicy(policyDir + file.getName());

            Duration runTime = Duration.ofSeconds(0);

            try {
                runTime = runTime.plus(runBasicQuery(basicQueries));
                policyRunTimes.put(file.getName(), runTime);
            } catch (Exception e) {
                e.printStackTrace();
                policyRunTimes.put(file.getName(), PolicyConstants.MAX_DURATION);
            }
        }
        return policyRunTimes;
    }

    private List<RangeQuery> readRangePolicy(String fileName){
        String values = Reader.readTxt(fileName);
        List<RangeQuery> rangeQueries = new ArrayList<RangeQuery>();
        try {
            rangeQueries.addAll(objectMapper.readValue(values,
                    new TypeReference<List<RangeQuery>>() {
                    }));
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rangeQueries;
    }

    public Duration runRangeQuery(List<RangeQuery> rangeQueries){
        String query = "SELECT * FROM SEMANTIC_OBSERVATION " +
                "WHERE " + IntStream.range(0, rangeQueries.size() ).mapToObj(i-> rangeQueries.get(i).createPredicate())
                .collect(Collectors.joining(" OR "));

        try {
            return mySQLQueryManager.runWithThread(query, "rq");
        } catch (Exception e) {
            e.printStackTrace();
            throw new PolicyEngineException("Error Running Query");
        }
    }

    public Map<String, Duration> runRangeQueries(String policyDir) {

        Map<String, Duration> policyRunTimes = new HashMap<>();

        File[] policyFiles = new File(policyDir).listFiles();

        String values = null;

        for (File file : policyFiles) {

            List<RangeQuery> rangeQueries = readRangePolicy(policyDir + file.getName());

            Duration runTime = Duration.ofSeconds(0);

            try {
                runTime = runTime.plus(runRangeQuery(rangeQueries));
                policyRunTimes.put(file.getName(), runTime);
            } catch (Exception e) {
                e.printStackTrace();
                policyRunTimes.put(file.getName(), PolicyConstants.MAX_DURATION);
            }
        }

        return policyRunTimes;
    }



    public Map<String, Duration> runBEPolicies(String policyDir) {

        Map<String, Duration> policyRunTimes = new HashMap<>();

        File[] policyFiles = new File(policyDir).listFiles();

        Boolean resultsChecked = false;

        for (File file : policyFiles) {

            System.out.println(file.getName() + " being processed........");

            String results_file = new SimpleDateFormat("yyyyMMddHHmm").format(new Date());

            BEExpression beExpression = new BEExpression();

            beExpression.parseJSONList(Reader.readTxt(policyDir + file.getName()));

            Duration runTime = Duration.ofMillis(0);

            System.out.println("Number Of Predicates before extension: " + beExpression.countNumberOfPredicates());

            try {
                /** Traditional approach **/
                System.out.println(beExpression.createQueryFromPolices());
                runTime = runTime.plus(mySQLQueryManager.runTimedQuery(beExpression.createQueryFromPolices(),
                        results_file));
                policyRunTimes.put(file.getName(), runTime);
                System.out.println(file.getName() + " completed and took " + runTime);

                /** Extension **/
//                runTime = Duration.ofMillis(0);
                PredicateExtension f = new PredicateExtension(beExpression);
                f.approximateFactorization();
                System.out.println("Number Of Predicates after extension: " + f.getExpression().countNumberOfPredicates());
//                runTime = runTime.plus(mySQLQueryManager.runTimedQuery(f.getExpression().createQueryFromPolices(),
//                        PolicyConstants.QR_EXTENDED + results_file));
//                System.out.println("**" + file.getName() + "-extended completed and took " + runTime + "**");
//                Instant sA = Instant.now();
//                Instant eA = Instant.now();
//                System.out.println("Extension took " + Duration.between(sA, eA));
                writer.writeJSONToFile(f.getExpression().getPolicies(), PolicyConstants.BE_POLICY_DIR, null);
//                resultsChecked = mySQLQueryManager.checkResults(PolicyConstants.QR_EXTENDED + results_file);
//                if(!resultsChecked){
//                    System.out.println("*** Query results don't match after Extension ***!!!");
//                    policyRunTimes.put(file.getName() + "-af-invalid", PolicyConstants.MAX_DURATION);
//                }
//                else {
//                    policyRunTimes.put(file.getName() + "-af", runTime);
//                    writer.writeJSONToFile(f.getExpression().getPolicies(), PolicyConstants.BE_POLICY_DIR, null);
//                }


//                BEExpression approxExpression = new BEExpression();
//                approxExpression.parseJSONList(Reader.readTxt(policyDir + file.getName()));
//                FactorSelection gf = new FactorSelection(approxExpression);


                /** Factorization **/
                FactorSelection gf = new FactorSelection(f.getExpression());
//                Instant sG = Instant.now();
                gf.GFactorize();
//                Instant eG = Instant.now();
                List <ObjectCondition> guards = gf.getIndexFilters();
                System.out.println("Number of guards: " + guards.size());
                System.out.println(gf.createQueryFromExactFactor());
                System.out.println("Summed cost of guards: " + gf.computeGuardCosts());
//                System.out.println("Length of remainder: " + gf.lengthOfRemainder());

//                System.out.println("Number of tuples satisfied by guards : "+ mySQLQueryManager.runCountingQuery(BEExpression.createQueryFromGuards(guards)));
//                System.out.println("Factorization took " + Duration.between(sG, eG));
//                System.out.println("Factorized query " + gf.createQueryFromExactFactor());
//                runTime = Duration.ofMillis(0);
                runTime = runTime.plus(mySQLQueryManager.runTimedQuery(gf.createQueryFromExactFactor(),
                        PolicyConstants.QR_FACTORIZED + results_file));
//                policyRunTimes.put(file.getName() + "-gf", runTime);
                System.out.println("** Factorized query took " + runTime + " **");


            } catch (Exception e) {
                e.printStackTrace();
                policyRunTimes.put(file.getName(), PolicyConstants.MAX_DURATION);
            }
        }

        return policyRunTimes;
    }

    //Only handles BEPolicies
    private void generatePolicies(String policyDir) {
        List<BEPolicy> bePolicies = new ArrayList<>();
        for (int i = 0; i < policyNumbers.length; i++) {
            List<String> attributes = new ArrayList<>();
            attributes.add(PolicyConstants.TIMESTAMP_ATTR);
            attributes.add(PolicyConstants.USERID_ATTR);
            attributes.add(PolicyConstants.ENERGY_ATTR);
            attributes.add(PolicyConstants.TEMPERATURE_ATTR);
            attributes.add(PolicyConstants.LOCATIONID_ATTR);
            List<BEPolicy> genPolicy = policyGen.generateBEPolicy(policyNumbers[i], attributes, bePolicies);
            bePolicies.clear();
            bePolicies.addAll(genPolicy);
        }
    }

    private void basicQueryExperiments(String policyDir){
        Map<String, Duration> runTimes = new HashMap<>();
        runTimes.putAll(runBasicQueries(policyDir));
        writer.createTextReport(runTimes, policyDir);
    }


    private void rangeQueryExperiments(String policyDir){
        Map<String, Duration> runTimes = new HashMap<>();
        runTimes.putAll(runRangeQueries(policyDir));
        writer.createTextReport(runTimes, policyDir);
    }

    private void bePolicyExperiments(String policyDir){
        Map<String, Duration> runTimes = new HashMap<>();
        runTimes.putAll(runBEPolicies(policyDir));
        writer.createTextReport(runTimes, policyDir);
    }


    public static void main (String args[]){
        PolicyExecution pe = new PolicyExecution();
//        pe.generatePolicies(PolicyConstants.BASIC_POLICY_1_DIR);
//        pe.basicQueryExperiments(PolicyConstants.BASIC_POLICY_1_DIR);
//        pe.generatePolicies(PolicyConstants.BASIC_POLICY_2_DIR);
//        pe.basicQueryExperiments(PolicyConstants.BASIC_POLICY_2_DIR);
//        pe.generatePolicies(PolicyConstants.RANGE_POLICY_1_DIR);
//        pe.rangeQueryExperiments(PolicyConstants.RANGE_POLICY_1_DIR);
//        pe.generatePolicies(PolicyConstants.RANGE_POLICY_2_DIR);
//        pe.rangeQueryExperiments(PolicyConstants.RANGE_POLICY_2_DIR);

        pe.generatePolicies(PolicyConstants.BE_POLICY_DIR);

        pe.bePolicyExperiments(PolicyConstants.BE_POLICY_DIR);
    }
}
