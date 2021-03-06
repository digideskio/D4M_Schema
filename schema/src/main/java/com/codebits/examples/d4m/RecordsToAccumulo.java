package com.codebits.examples.d4m;

import com.codebits.d4m.PropertyManager;
import com.codebits.d4m.TableManager;
import com.codebits.d4m.ingest.MutationFactory;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Properties;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.data.Mutation;

public class RecordsToAccumulo {

    //private static final Logger log = Logger.getLogger(WriteMultipleExplodedRecords.class);
    private final Charset charset = Charset.defaultCharset();

    String[] fieldNames = {
        "CITY_NAME",
        "STATE_NAME",
        "ZIPCODE"
    };

    String[] records = {
        "Akron\tIOWA\t51001"
        ,"Fairfax\tVIRGINIA\t22033"
        ,"Arlington\tVIRGINIA\t22101"
    };

    public static void main(String[] args) throws IOException, AccumuloException, AccumuloSecurityException, TableNotFoundException {
        RecordsToAccumulo driver = new RecordsToAccumulo();
        driver.process();
    }

    private void process() throws AccumuloException, AccumuloSecurityException, TableNotFoundException, IOException {
        PropertyManager propertyManager = new PropertyManager();
        propertyManager.setPropertyFilename("d4m.properties");
        Properties properties = propertyManager.load();

        String instanceName = properties.getProperty("accumulo.instance.name");
        String zooKeepers = properties.getProperty("accumulo.zookeeper.ensemble");
        String user = properties.getProperty("accumulo.user");
        byte[] pass = properties.getProperty("accumulo.password").getBytes(charset);

        ZooKeeperInstance instance = new ZooKeeperInstance(instanceName, zooKeepers);
        Connector connector = instance.getConnector(user, pass);

        TableManager tableManager = new TableManager(connector, connector.tableOperations());

        MutationFactory factory = new MutationFactory("\t", "|");

        BatchWriter edgeWriter = null;
        BatchWriter transposeWriter = null;
        BatchWriter degreeWriter = null;
        BatchWriter textWriter = null;

        try {
            edgeWriter = connector.createBatchWriter(tableManager.getEdgeTable(), 10000000, 10000, 5);
            transposeWriter = connector.createBatchWriter(tableManager.getTransposeTable(), 10000000, 10000, 5);
            degreeWriter = connector.createBatchWriter(tableManager.getDegreeTable(), 10000000, 10000, 5);
            textWriter = connector.createBatchWriter(tableManager.getTextTable(), 10000000, 10000, 5);

            for (String record : records) {
                String[] fieldValues = record.split("\t");
                String row = fieldNames[2] + "|" + fieldValues[2];

                edgeWriter.addMutation(factory.generateEdges(row, fieldNames, fieldValues));

                for (Mutation mutation : factory.generateTranspose(row, fieldNames, fieldValues)) {
                    transposeWriter.addMutation(mutation);
                }
                for (Mutation mutation : factory.generateDegree(row, fieldNames, fieldValues)) {
                    degreeWriter.addMutation(mutation);
                }
                textWriter.addMutation(factory.generateRawData(row, fieldNames, fieldValues));
            }

        } finally {
            if (edgeWriter != null) {
                edgeWriter.close();
            }
            if (transposeWriter != null) {
                transposeWriter.close();
            }
            if (degreeWriter != null) {
                degreeWriter.close();
            }
            if (textWriter != null) {
                textWriter.close();
            }
        }

    }

}
