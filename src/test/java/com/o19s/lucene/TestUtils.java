package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

public class TestUtils {

    /**
     * Helper function to create an index and index documents.
     *
     * @param fieldname Document field name
     * @param docs      Array of document strings
     * @param analyzer  Analyzer
     * @return Created Directory object
     */
    public static Directory index(String fieldname, String[] docs, Analyzer analyzer) {
        Directory directory = null;
        try {
            // Store the index in memory:
            directory = new RAMDirectory();
            // To store an index on disk, use this instead:
            //Directory directory = FSDirectory.open("/tmp/testindex");
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            IndexWriter iwriter = new IndexWriter(directory, config);

            FieldType fieldType = new FieldType(TextField.TYPE_STORED);
            fieldType.setTokenized(true);
            fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);
            fieldType.setStoreTermVectors(true);
            fieldType.setStoreTermVectorPositions(true);
            fieldType.setStoreTermVectorOffsets(true);

            for (String text : docs) {
                Document doc = new Document();
                doc.add(new Field(fieldname, text, fieldType));
                iwriter.addDocument(doc);
                System.out.println(String.format("Indexed: \"%s\"", text));
            }
            iwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return directory;
    }
}
