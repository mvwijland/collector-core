/* Copyright 2014-2017 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.core.data.store.impl.mongo;

import static com.mongodb.client.model.Filters.eq;

import java.util.Date;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.bson.Document;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.norconex.collector.core.data.BaseCrawlData;
import com.norconex.collector.core.data.CrawlState;
import com.norconex.collector.core.data.ICrawlData;
import com.norconex.commons.lang.StringUtil;
import com.norconex.commons.lang.file.ContentType;

/**
 * Basic Mongo serializer for {@link BaseCrawlData} instances.
 * @author Pascal Essiembre
 */
public class BaseMongoSerializer implements IMongoSerializer {

    @Override
    public Document toDocument(Stage stage, ICrawlData crawlData) {
 
        Document doc = new Document();
        
        // "reference" is a Mongo indexed field, which is limited to 1024.
        // So if too long we truncate it, trying to keep it unique,
        // while storing the original in a separate field.
        String ref = StringUtil.truncateWithHash(
                crawlData.getReference(), 1024, "!");
        doc.put(FIELD_REFERENCE, ref);
        if (!Objects.equals(ref, crawlData.getReference())) {
            doc.put(FIELD_REFERENCE_EXCESSIVE, crawlData.getReference());
        }
        
        doc.put(FIELD_PARENT_ROOT_REFERENCE, 
                crawlData.getParentRootReference());
        doc.put(FIELD_IS_ROOT_PARENT_REFERENCE, 
                crawlData.isRootParentReference());
        doc.put(FIELD_CRAWL_STATE, crawlData.getState().toString());
        doc.put(FIELD_META_CHECKSUM, crawlData.getMetaChecksum());
        doc.put(FIELD_CONTENT_CHECKSUM, crawlData.getContentChecksum());
        doc.put(FIELD_IS_VALID, crawlData.getState().isGoodState());
        doc.put(FIELD_STAGE, stage.toString());
        if (crawlData.getContentType() != null) {
            doc.put(FIELD_CONTENT_TYPE, crawlData.getContentType().toString());
        }
        doc.put(FIELD_CRAWL_DATE, crawlData.getCrawlDate());
        return doc;
    }

    @Override
    public ICrawlData fromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        BaseCrawlData data = new BaseCrawlData();
      
        // If the doc has an excessively long URL, make sure to use it
        // and ignore the indexed one that was truncated.
        String ref = doc.getString(FIELD_REFERENCE_EXCESSIVE);
        if (StringUtils.isBlank(ref)) {
            ref = doc.getString(FIELD_REFERENCE);
        }
        data.setReference(ref);
        
        data.setParentRootReference(doc.getString(FIELD_PARENT_ROOT_REFERENCE));
        data.setRootParentReference(
                doc.getBoolean(FIELD_IS_ROOT_PARENT_REFERENCE));
        String crawlState = doc.getString(FIELD_CRAWL_STATE);
        if (crawlState != null) {
            data.setState(CrawlState.valueOf(crawlState));
        }
        data.setMetaChecksum((String) doc.get(FIELD_META_CHECKSUM));
        data.setContentChecksum((String) doc.get(FIELD_CONTENT_CHECKSUM));
        String contentType = (String) doc.get(FIELD_CONTENT_TYPE);
        if (StringUtils.isNotBlank(contentType)) {
            data.setContentType(ContentType.valueOf(contentType));
        }
        data.setCrawlDate((Date) doc.get(FIELD_CRAWL_DATE));
        return data;
    }

    @Override
    public Document getNextQueued(MongoCollection<Document> collRefs) {
        Document newDocument = new Document(
                "$set", new Document(FIELD_STAGE, Stage.ACTIVE.name()));
        return collRefs.findOneAndUpdate(
                eq(FIELD_STAGE, Stage.QUEUED.name()), newDocument);
    }

    @Override
    public void createIndices(
            MongoCollection<Document> referenceCollection, 
            MongoCollection<Document> cachedCollection) {
        ensureIndex(referenceCollection, true, FIELD_REFERENCE);
        ensureIndex(cachedCollection, true, FIELD_REFERENCE);
        ensureIndex(referenceCollection, false, FIELD_IS_VALID);
        ensureIndex(referenceCollection, false, FIELD_STAGE);
        ensureIndex(referenceCollection, false, 
        		FIELD_STAGE, FIELD_DEPTH);
    }

    protected final void ensureIndex(
            MongoCollection<Document> coll, boolean unique, String... fields) {
        Document fieldsObject = new Document();
        for (String field : fields) {
            fieldsObject.append(field, 1);
        }
        coll.createIndex(fieldsObject, new IndexOptions().unique(unique));
    }
}
