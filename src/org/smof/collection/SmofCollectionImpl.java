package org.smof.collection;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.bson.BsonDocument;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.smof.element.Element;
import org.smof.exception.SmofException;
import org.smof.parsers.SmofParser;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;

class SmofCollectionImpl<T extends Element> implements SmofCollection<T> {

	private static final int CACHE_SIZE = 200;
	
	private static void handleError(Throwable cause) {
		throw new SmofException(cause);
	}
	
	protected final MongoCollection<BsonDocument> collection;
	private final Class<T> type;
	private final String name;
	private final SmofParser parser;
	private final Cache<ObjectId, T> cache;

	SmofCollectionImpl(String name, MongoCollection<BsonDocument> collection, Class<T> type, SmofParser parser) {
		this.collection = collection;
		this.name = name;
		this.parser = parser;
		this.type = type;
		cache = CacheBuilder.newBuilder()
				.maximumSize(CACHE_SIZE)
				.expireAfterAccess(5, TimeUnit.MINUTES)
				.build();
	}

	@Override
	public void insert(final T element) {
		if(!cache.asMap().containsKey(element.getId())) {
			final BsonDocument document = parser.toBson(element);
			collection.insertOne(document);
			cache.put(element.getId(), element);
		}
	}
	
	@Override
	public SmofQuery<T> query() {
		final FindIterable<BsonDocument> rawQuery = collection.find();
		return new SmofQuery<T>(type, rawQuery, parser, cache);
	}
	
	@Override
	public T findById(ObjectId id) {
		try {
			return cache.get(id, new IdLoader(id));
		} catch (ExecutionException e) {
			handleError(e);
			return null;
		}
	}
	
	@Override
	public void execUpdate(Bson filter, Bson update) {
		collection.updateMany(filter, update);
	}

	@Override
	public SmofUpdate<T> update() {
		return new SmofUpdate<>(this);
	}

	@Override
	public void replace(T element) {
		final Bson query = Filters.eq(Element.ID, element.getId());
		final BsonDocument document = parser.toBson(element);
		collection.findOneAndReplace(query, document);
	}

	@Override
	public String getCollectionName() {
		return name;
	}

	@Override
	public MongoCollection<BsonDocument> getMongoCollection() {
		return collection;
	}
	
	@Override
	public SmofParser getParser() {
		return parser;
	}

	@Override
	public Class<T> getType() {
		return type;
	}

	private class IdLoader implements Callable<T> {
	
		private final ObjectId id;
		
		private IdLoader(ObjectId id) {
			super();
			this.id = id;
		}
	
		@Override
		public T call() throws Exception {
			final Bson idFilter = Filters.eq(Element.ID, id);
			final FindIterable<BsonDocument> query = collection.find(idFilter);
			return parser.fromBson(query.first(), type);
		}
		
	}

}
