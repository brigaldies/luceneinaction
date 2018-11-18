package com.o19s.lucene;

import junit.framework.TestCase;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.search.spans.*;
import org.apache.lucene.store.RAMDirectory;
import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

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

    private final int END_OF_TOKEN_POSITION = 2147483647;

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
        dumpSpans(brown);
    }

    @Test
    public void testSpanFirstQuery() throws Exception {
        // Search for "brown" within the first two positions
        SpanFirstQuery sfq = new SpanFirstQuery(brown, 2);
        dumpSpans(sfq);
        assertNoMatches(sfq);

        // Search for "brown" within the first three positions
        sfq = new SpanFirstQuery(brown, 3);
        dumpSpans(sfq);
        assertOnlyBrownFox(sfq);
    }

    @Test
    public void testSpanNearQuery() throws Exception {
        SpanQuery[] quick_brown_dog = new SpanQuery[]{quick, brown, dog};

        SpanNearQuery snq = new SpanNearQuery(quick_brown_dog, 0, true);
        dumpSpans(snq);
        assertNoMatches(snq);

        snq = new SpanNearQuery(quick_brown_dog, 4, true);
        dumpSpans(snq);
        assertNoMatches(snq);

        snq = new SpanNearQuery(quick_brown_dog, 5, true);
        dumpSpans(snq);
        assertOnlyBrownFox(snq);

        // interesting - even a sloppy phrase query would require
        // more slop to match
        snq = new SpanNearQuery(new SpanQuery[]{lazy, fox}, 3, false);
        dumpSpans(snq);
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
        dumpSpans(quick_fox);
        assertBothFoxes(quick_fox);

        SpanNotQuery quick_fox_dog = new SpanNotQuery(quick_fox, dog);
        dumpSpans(quick_fox_dog);
        assertBothFoxes(quick_fox_dog);

        SpanNotQuery no_quick_red_fox = new SpanNotQuery(quick_fox, red);
        dumpSpans(no_quick_red_fox);
        assertOnlyBrownFox(no_quick_red_fox);
    }

    @Test
    public void testSpanOrQuery() throws Exception {
        SpanNearQuery quick_fox = new SpanNearQuery(new SpanQuery[]{quick, fox}, 1, true);
        SpanNearQuery lazy_dog = new SpanNearQuery(new SpanQuery[]{lazy, dog}, 0, true);
        SpanNearQuery sleepy_cat = new SpanNearQuery(new SpanQuery[]{sleepy, cat}, 0, true);
        SpanNearQuery qf_near_ld = new SpanNearQuery(new SpanQuery[]{quick_fox, lazy_dog}, 3, true);
        dumpSpans(qf_near_ld);
        assertOnlyBrownFox(qf_near_ld);

        SpanNearQuery qf_near_sc = new SpanNearQuery(new SpanQuery[]{quick_fox, sleepy_cat}, 3, true);
        dumpSpans(qf_near_sc);
        assertOnlyRedFox(qf_near_sc);

        SpanOrQuery or = new SpanOrQuery(qf_near_ld, qf_near_sc);
        dumpSpans(or);
        assertBothFoxes(or);
    }

    @Test
    public void testDumpSpans() {
        try {
            dumpSpans(new SpanTermQuery(new Term("f", "the")));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Helper method to show the spans.
     *
     * @param query Query
     * @throws IOException
     */
    private void dumpSpans(SpanQuery query) throws IOException {

        System.out.println(String.format("\nQuery: %s", query));
        int numSpans = 0;
        TopDocs hits = searcher.search(query, 10);
        float[] scores = new float[2];
        for (ScoreDoc sd : hits.scoreDocs) {
            scores[sd.doc] = sd.score;
        }

        SpanWeight spanWeight = query.createWeight(searcher, false, 1.0f);

        int contextCount = 0;
        for (LeafReaderContext context : reader.leaves()) {
            contextCount++;

            Spans spans = spanWeight.getSpans(context, SpanWeight.Postings.POSITIONS);

            int doc_id;
            while ((doc_id = spans.nextDoc()) != spans.NO_MORE_DOCS) {
                numSpans++;
//                System.out.println(spans.toString());
//                System.out.println("doc_id=" + doc_id);
                Document doc = reader.document(doc_id);
                System.out.println(String.format("Doc id %d: %s", doc_id, doc.getField("f")));
//                System.out.println(spans.nextStartPosition());
//                System.out.println(spans.endPosition());

                TokenStream stream = analyzer.tokenStream("f", new StringReader(doc.get("f")));
                CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
                stream.reset();
                StringBuilder buffer = new StringBuilder();

                // Initializations
                int i = 0;
                int lastStartPosition = spans.nextStartPosition();
                int lastEndPosition = spans.endPosition();

                while (stream.incrementToken()) {
                    String token = term.toString().trim();
                    // System.out.println(String.format("[%d] term: %s", i, token));
                    // System.out.println(String.format("Start: %d, End: %d", lastStartPosition, lastEndPosition));

                    if (i == lastStartPosition) {
                        buffer.append("<");

                        if (lastStartPosition != END_OF_TOKEN_POSITION) {
                            lastStartPosition = spans.nextStartPosition();
                        }
                    }

                    buffer.append(token);

                    if (i + 1 == lastEndPosition) {
                        buffer.append(">");

                        if (lastEndPosition != END_OF_TOKEN_POSITION) {
                            lastEndPosition = spans.endPosition();
                        }
                    }

                    buffer.append(" ");

                    i++;

                }

                System.out.println(String.format("Context %d, span %d: %s", contextCount, numSpans, buffer));

                stream.end();
                stream.close();
            }

//            System.out.println(String.format("Spans count: %d", numSpans));
        }

//        System.out.println(String.format("Context count: %d", contextCount));
    }
}
