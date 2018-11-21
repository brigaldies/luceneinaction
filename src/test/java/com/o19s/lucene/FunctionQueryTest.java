package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queries.function.FunctionQuery;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.valuesource.TermFreqValueSource;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.SpanNearQuery;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.util.BytesRef;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        analyzer = new StandardAnalyzer();
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(directory, config);

        // Analyzed field type
        FieldType fieldType = new FieldType(TextField.TYPE_STORED);
        fieldType.setTokenized(true);
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS);

        // Index documents
        Document doc = new Document();
        doc.add(new Field("f",
                "the quick brown fox and red fox jump over the lazy dog. The red fox jumped higher over the lazy dog.",
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

    @Test
    public void testPhraseFreq() throws Exception {

        // Traditional phrase query
        PhraseQuery.Builder builder = new PhraseQuery.Builder()
                .add(new Term("f", "red"))
                .add(new Term("f", "fox"))
                .setSlop(3);
        PhraseQuery phraseQuery = builder.build();

        TopDocs topDocs = searcher.search(phraseQuery, 10);
        ScoreDoc[] scoredocs = topDocs.scoreDocs;
        System.out.println("With PhraseQuery:");
        for (ScoreDoc doc : scoredocs) {
            System.out.println(String.format("Doc: %s", doc));
        }
        assertEquals(2, topDocs.totalHits);

        // Span query
        SpanTermQuery red = new SpanTermQuery(new Term("f", "red"));
        SpanTermQuery fox = new SpanTermQuery(new Term("f", "fox"));
        SpanQuery[] red_fox = new SpanQuery[]{red, fox};
        SpanNearQuery snq = new SpanNearQuery(red_fox, 0, true);

        TestUtils.dumpSpans(snq, searcher, reader);
        assertEquals(2, topDocs.totalHits);

        // Simulate a custom query that acts as an LA atleastN(Span Query)
        System.out.println("With atleastNSpan:");
        List<Document> docs = atleastNSpan(snq, 2);
        for (Document doc : docs) {
            System.out.println(String.format("Doc %s", doc));
        }
        assertEquals(1, docs.size());
    }

    @Test
    public void testNestedSpanFreq() throws Exception {

        TopDocs topDocs;

        // Span1: red ... fox
        SpanTermQuery red = new SpanTermQuery(new Term("f", "red"));
        SpanTermQuery fox = new SpanTermQuery(new Term("f", "fox"));
        SpanNearQuery redFoxSpanQ = new SpanNearQuery(new SpanQuery[]{red, fox}, 2, true);


        // Span2: lazy ... dog
        SpanTermQuery lazy = new SpanTermQuery(new Term("f", "lazy"));
        SpanTermQuery dog = new SpanTermQuery(new Term("f", "dog"));
        SpanNearQuery lazyDogSpanQ = new SpanNearQuery(new SpanQuery[]{lazy, dog}, 2, true);

        // Span: span1 ... span2
        SpanNearQuery spansSpanQ = new SpanNearQuery(new SpanQuery[]{redFoxSpanQ, lazyDogSpanQ}, 5, true);

        topDocs = searcher.search(spansSpanQ, 10);
        assertEquals(1, topDocs.totalHits);

        TestUtils.dumpSpans(spansSpanQ, searcher, reader);

        System.out.println("With atleastNSpan:");
        List<Document> docs = atleastNSpan(spansSpanQ, 2);
        for (Document doc : docs) {
            System.out.println(String.format("Doc: f=\"%s\"", doc.getField("f")));
        }
        assertEquals(1, docs.size());
    }

    /**
     * Search using a Span Query and retrieves the matched documents whose matched spans occur at least a given number.
     * <p>
     * ATTENTION: This would not work in a Solr query parser plugin environment as the plugin must return a query to Solr.
     * Hence, we need a query that implements counting the the number of spans.
     *
     * @param spanQuery   The span query
     * @param minSpanFreq The minimum frequency.
     * @return The list of matched docs.
     * @throws Exception An exception occurred.
     */
    private List<Document> atleastNSpan(SpanQuery spanQuery, int minSpanFreq) throws Exception {
        Spans spans = TestUtils.getSpans(spanQuery, searcher, reader);

        List<Document> docs = new ArrayList<Document>();

        int docCount = 0;
        int doc_id;
        while ((doc_id = spans.nextDoc()) != spans.NO_MORE_DOCS) {

            // Count the number of spans
            int spansCount = 0;
            while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
                spansCount++;
            }
            System.out.println(String.format("Doc id %d: %d spans", doc_id, spansCount));

            if (spansCount >= minSpanFreq) {
                docs.add(reader.document(doc_id));
            }
        }

        return docs;
    }
}
