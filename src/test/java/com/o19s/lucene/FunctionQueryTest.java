package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.valuesource.TermFreqValueSource;
import org.apache.lucene.search.*;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static junit.framework.TestCase.assertEquals;

public class FunctionQueryTest {

    private static RAMDirectory directory;
    private static IndexSearcher searcher;
    private static IndexReader reader;
    private static Analyzer analyzer;

    @BeforeClass
    public static void testSetup() throws IOException {
        System.out.println("testSetup");

        // Create a RAM-based index for the tests
        directory = new RAMDirectory();
        analyzer = new WhitespaceAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(directory, config);

        // Analyzed field type
        FieldType fieldType = new FieldType(TextField.TYPE_STORED);
        fieldType.setTokenized(true);
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);

        // Index documents
        Document doc = new Document();
        doc.add(new Field("f",
                "the quick brown fox and red fox jump over the lazy dog",
                fieldType));
        writer.addDocument(doc);
        doc = new Document();
        doc.add(new Field("f",
                "the quick red fox jumps over the sleepy cat",
                fieldType));
        writer.addDocument(doc);
        writer.close();

        // Create and keep a searcher for the duration of the tests
        searcher = new IndexSearcher(DirectoryReader.open(directory));

        // Create and keep an index reader
        reader = searcher.getIndexReader();
    }

    @AfterClass
    public static void testTeardown() throws IOException {
        System.out.println("testTeardown");
        reader.close();
        directory.close();

    }

    @Test
    public void testFunctionTermFreq() throws IOException {
        String fieldName = "f";
        String searchTerm = "fox";
        Query functionQuery = new FunctionQuery(
                new TermFreqValueSource(fieldName, searchTerm, fieldName, new BytesRef(searchTerm)));

        System.out.println(String.format("Function range query: %s", functionQuery));

        TopDocs topDocs = searcher.search(functionQuery, 10);
        ScoreDoc[] docs = topDocs.scoreDocs;
        for (ScoreDoc doc : docs) {
            System.out.println(String.format("Doc: %s", doc));
        }
        assertEquals(2, topDocs.totalHits);
    }

    /**
     * WARNING: From functionRangeQuery.java: "This can be a slow query if run by itself since it must visit all docs;
     * ideally it's combined with other queries."
     *
     * @throws IOException An exception occurred.
     */
    @Test
    public void testFunctionRangeTermFreq() throws IOException {
        String fieldName = "f";
        String searchTerm = "fox";
        Number lowerVal = 2;

        Query functionRangeQuery = new FunctionRangeQuery(
                new TermFreqValueSource(fieldName, searchTerm, fieldName, new BytesRef(searchTerm)),
                lowerVal, null, true, true
        );

        System.out.println(String.format("Function range query: %s", functionRangeQuery));

        // Combined the function range query with a term query so that the search does not scan the entire index!
        BooleanClause clauseTerm = new BooleanClause(
                new TermQuery(new Term(fieldName, searchTerm)),
                BooleanClause.Occur.MUST);

        BooleanClause clauseTermFreq = new BooleanClause(
                functionRangeQuery,
                BooleanClause.Occur.MUST
        );

        BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();
        queryBuilder.add(clauseTerm);
        queryBuilder.add(clauseTermFreq);

        TopDocs topDocs = searcher.search(queryBuilder.build(), 10);
        ScoreDoc[] docs = topDocs.scoreDocs;
        for (ScoreDoc doc : docs) {
            System.out.println(String.format("Doc: %s", doc));
        }

        assertEquals(1, topDocs.totalHits);
    }
}
