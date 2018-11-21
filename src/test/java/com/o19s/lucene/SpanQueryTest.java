package com.o19s.lucene;

import junit.framework.TestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import java.io.IOException;

public class SpanQueryTest extends TestCase {

    private RAMDirectory directory;
    private IndexSearcher searcher;
    private IndexReader reader;
    private SpanTermQuery quick;
    private SpanTermQuery brown;
    private SpanTermQuery red;
    private SpanTermQuery fox;
    private SpanTermQuery lazy;
    private SpanTermQuery sleepy;
    private SpanTermQuery dog;
    private SpanTermQuery cat;
    private Analyzer analyzer;

    @Override
    protected void setUp() throws Exception {
        System.out.println(String.format("setUp"));

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
                "the quick brown fox jumps over the lazy dog",
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

        // Create and keep some span term queries
        quick = new SpanTermQuery(new Term("f", "quick"));
        brown = new SpanTermQuery(new Term("f", "brown"));
        red = new SpanTermQuery(new Term("f", "red"));
        fox = new SpanTermQuery(new Term("f", "fox"));
        lazy = new SpanTermQuery(new Term("f", "lazy"));
        sleepy = new SpanTermQuery(new Term("f", "sleepy"));
        dog = new SpanTermQuery(new Term("f", "dog"));
        cat = new SpanTermQuery(new Term("f", "cat"));
    }

    protected void tearDown() throws IOException {
//        searcher.close();
        directory.close();
    }

    /**
     * Assert that only the "brown fox" document was matched.
     *
     * @param query Query
     * @throws Exception
     */
    private void assertOnlyBrownFox(Query query) throws Exception {
        System.out.println(String.format("assertOnlyBrownFox"));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits);
        assertEquals("wrong doc", 0, hits.scoreDocs[0].doc);
    }

    /**
     * Assert that only the "red fox" document was matched.
     *
     * @param query Query
     * @throws Exception
     */
    private void assertOnlyRedFox(Query query) throws Exception {
        System.out.println(String.format("assertOnlyRedFox"));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits);
        assertEquals("wrong doc", 1, hits.scoreDocs[0].doc);
    }

    /**
     * Assert that both fox documents were matched.
     *
     * @param query Query
     * @throws Exception
     */
    private void assertBothFoxes(Query query) throws Exception {
        System.out.println(String.format("assertBothFoxes"));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(2, hits.totalHits);
    }

    /**
     * Assert that no document was matched.
     *
     * @param query Query
     * @throws Exception
     */
    private void assertNoMatches(Query query) throws Exception {
        System.out.println(String.format("assertNoMatches"));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(0, hits.totalHits);
    }

    /**
     * Test searching for the brown fox.
     *
     * @throws Exception
     */
    @Test
    public void testSpanTermQuery() throws Exception {
        assertOnlyBrownFox(brown);
        TestUtils.dumpSpans(brown, searcher, reader);
    }

    @Test
    public void testSpanFirstQuery() throws Exception {
        // Search for "brown" within the first two positions
        SpanFirstQuery sfq = new SpanFirstQuery(brown, 2);
        TestUtils.dumpSpans(sfq, searcher, reader);
        assertNoMatches(sfq);

        // Search for "brown" within the first three positions
        sfq = new SpanFirstQuery(brown, 3);
        TestUtils.dumpSpans(sfq, searcher, reader);
        assertOnlyBrownFox(sfq);
    }

    @Test
    public void testSpanNearQuery() throws Exception {
        SpanQuery[] quick_brown_dog = new SpanQuery[]{quick, brown, dog};

        SpanNearQuery snq = new SpanNearQuery(quick_brown_dog, 0, true);
        TestUtils.dumpSpans(snq, searcher, reader);
        assertNoMatches(snq);

        snq = new SpanNearQuery(quick_brown_dog, 4, true);
        TestUtils.dumpSpans(snq, searcher, reader);
        assertNoMatches(snq);

        snq = new SpanNearQuery(quick_brown_dog, 5, true);
        TestUtils.dumpSpans(snq, searcher, reader);
        assertOnlyBrownFox(snq);

        // interesting - even a sloppy phrase query would require
        // more slop to match
        snq = new SpanNearQuery(new SpanQuery[]{lazy, fox}, 3, false);
        TestUtils.dumpSpans(snq, searcher, reader);
        assertOnlyBrownFox(snq);

        PhraseQuery.Builder builder = new PhraseQuery.Builder()
                .add(new Term("f", "lazy"))
                .add(new Term("f", "fox"))
                .setSlop(4);
        PhraseQuery phraseQuery = builder.build();
        System.out.println(String.format("\nQuery: %s", phraseQuery));
        assertNoMatches(phraseQuery);

        builder.setSlop(5);
        phraseQuery = builder.build();
        System.out.println(String.format("\nQuery: %s", phraseQuery));
        assertOnlyBrownFox(phraseQuery);

    }

    @Test
    public void testSpanNotQuery() throws Exception {
        SpanNearQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick, fox}, 1, true);
        TestUtils.dumpSpans(quick_fox, searcher, reader);
        assertBothFoxes(quick_fox);

        SpanNotQuery quick_fox_dog = new SpanNotQuery(quick_fox, dog);
        TestUtils.dumpSpans(quick_fox_dog, searcher, reader);
        assertBothFoxes(quick_fox_dog);

        SpanNotQuery no_quick_red_fox = new SpanNotQuery(quick_fox, red);
        TestUtils.dumpSpans(no_quick_red_fox, searcher, reader);
        assertOnlyBrownFox(no_quick_red_fox);
    }

    @Test
    public void testSpanOrQuery() throws Exception {
        SpanNearQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick, fox}, 1, true);
        SpanNearQuery lazy_dog = new SpanNearQuery(new SpanQuery[]{lazy, dog}, 0, true);
        SpanNearQuery sleepy_cat = new SpanNearQuery(new SpanQuery[]{sleepy, cat}, 0, true);
        SpanNearQuery qf_near_ld = new SpanNearQuery(new SpanQuery[]{quick_fox, lazy_dog}, 3, true);
        TestUtils.dumpSpans(qf_near_ld, searcher, reader);
        assertOnlyBrownFox(qf_near_ld);

        SpanNearQuery qf_near_sc = new SpanNearQuery(new SpanQuery[]{quick_fox, sleepy_cat}, 3, true);
        TestUtils.dumpSpans(qf_near_sc, searcher, reader);
        assertOnlyRedFox(qf_near_sc);

        SpanOrQuery or = new SpanOrQuery(qf_near_ld, qf_near_sc);
        TestUtils.dumpSpans(or, searcher, reader);
        assertBothFoxes(or);
    }

    @Test
    public void testDumpSpans() {
        try {
            TestUtils.dumpSpans(new SpanTermQuery(new Term("f", "the")), searcher, reader);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
