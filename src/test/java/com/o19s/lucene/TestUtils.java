package com.o19s.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.SpanWeight;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;

import java.io.IOException;
import java.io.StringReader;

public class TestUtils {

    private static final int END_OF_TOKEN_POSITION = 2147483647;

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

    /**
     * Get the spans of a Span query.
     *
     * @param query    The Span query.
     * @param searcher An index searcher.
     * @param reader   An index reader.
     * @return The Span query's spans.
     */
    public static Spans getSpans(SpanQuery query, IndexSearcher searcher, IndexReader reader) throws Exception {
        System.out.println(String.format("\nQuery: %s", query));

        SpanWeight spanWeight = query.createWeight(searcher, false, 1.0f);

        LeafReaderContext readerContext = reader.leaves().get(0);

        return spanWeight.getSpans(readerContext, SpanWeight.Postings.POSITIONS);
    }

    /**
     * Helper method to show the spans.
     * <p>
     * ATTENTION: The loop 'while ((doc_id = spans.nextDoc()) != spans.NO_MORE_DOCS)' executes a full search.
     *
     * @param query Query
     * @throws IOException An exception occurred.
     */
    public static void dumpSpans(SpanQuery query, IndexSearcher searcher, IndexReader reader) throws Exception {

        boolean showStreamTokens = false;

        Spans spans = getSpans(query, searcher, reader);

        int docCount = 0;
        int doc_id;
        while ((doc_id = spans.nextDoc()) != spans.NO_MORE_DOCS) {
            docCount++;
            System.out.println(spans.toString());
            Document doc = reader.document(doc_id);

            // See the raw field value
//            System.out.println(String.format("Doc id %d: %s", doc_id, doc.getField("f")));

            // Get a stream token to scan the field value
            // Use the whitespace analyzer in order to count the stop word positions let empty by the standard analuyzer.
            Analyzer wsAnalyzer = new WhitespaceAnalyzer();
            TokenStream stream = wsAnalyzer.tokenStream("f", new StringReader(doc.get("f")));
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            StringBuilder buffer = new StringBuilder();

            // Initializations
            int i = 0;
            int nextStartPosition = spans.nextStartPosition();
            int nextEndPosition = spans.endPosition();

            // spans count
            int spansCount = 0;
//            while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
//                spansCount++;
//            }
//            System.out.println(String.format("Doc id %d: %d spans", doc_id, spansCount));

            while (stream.incrementToken()) {
                String token = term.toString().trim();
                if (showStreamTokens) {
                    System.out.println(String.format("[%d] term: %s", i, token));
                    System.out.println(String.format("Start: %d, End: %d", nextStartPosition, nextEndPosition));
                }

                if (i == nextStartPosition) {
                    buffer.append("<");

                    if (nextStartPosition != Spans.NO_MORE_POSITIONS) {
                        nextStartPosition = spans.nextStartPosition();
                    }
                }

                buffer.append(token);

                if (i + 1 == nextEndPosition) {
                    spansCount++;
                    buffer.append(">");

                    if (nextEndPosition != Spans.NO_MORE_POSITIONS) {
                        nextEndPosition = spans.endPosition();
                    }
                }

                buffer.append(" ");

                i++;

            }

            System.out.println(String.format("Doc id %d, spans count %d: %s", doc_id, spansCount, buffer));

            stream.end();
            stream.close();
        }

        System.out.println(String.format("Docs count: %d", docCount));
    }

}
