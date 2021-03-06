/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.crunch.types.avro;

import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.specific.SpecificRecord;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.hadoop.fs.Path;

import org.apache.crunch.MapFn;
import org.apache.crunch.SourceTarget;
import org.apache.crunch.fn.IdentityFn;
import org.apache.crunch.io.avro.AvroFileSourceTarget;
import org.apache.crunch.types.Converter;
import org.apache.crunch.types.PType;
import org.apache.crunch.types.PTypeFamily;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/**
 * The implementation of the PType interface for Avro-based serialization.
 * 
 */
public class AvroType<T> implements PType<T> {

	private static final Converter AVRO_CONVERTER = new AvroKeyConverter();

	private final Class<T> typeClass;
  private final String schemaString;
  private transient Schema schema;
	private final MapFn baseInputMapFn;
	private final MapFn baseOutputMapFn;
	private final List<PType> subTypes;
  private AvroDeepCopier<T> deepCopier;

	public AvroType(Class<T> typeClass, Schema schema, PType... ptypes) {
		this(typeClass, schema, IdentityFn.getInstance(), IdentityFn
				.getInstance(), ptypes);
	}

	public AvroType(Class<T> typeClass, Schema schema, MapFn inputMapFn,
			MapFn outputMapFn, PType... ptypes) {
		this.typeClass = typeClass;
		this.schema = Preconditions.checkNotNull(schema);
		this.schemaString = schema.toString();
		this.baseInputMapFn = inputMapFn;
		this.baseOutputMapFn = outputMapFn;
		this.subTypes = ImmutableList.<PType> builder().add(ptypes).build();
	}

	@Override
	public Class<T> getTypeClass() {
		return typeClass;
	}

	@Override
	public PTypeFamily getFamily() {
		return AvroTypeFamily.getInstance();
	}

	@Override
	public List<PType> getSubTypes() {
		return subTypes;
	}

	public Schema getSchema() {
	  if (schema == null){
	    schema = new Schema.Parser().parse(schemaString);
	  }
		return schema;
	}

	/**
	 * Determine if the wrapped type is a specific data avro type.
	 * 
	 * @return true if the wrapped type is a specific data type
	 */
	public boolean isSpecific() {
		if (SpecificRecord.class.isAssignableFrom(typeClass)) {
			return true;
		}
		for (PType ptype : subTypes) {
			if (SpecificRecord.class.isAssignableFrom(ptype.getTypeClass())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Determine if the wrapped type is a generic data avro type.
	 * 
	 * @return true if the wrapped type is a generic type
	 */
	public boolean isGeneric() {
		return GenericData.Record.class.equals(typeClass);
	}

	public MapFn<Object, T> getInputMapFn() {
		return baseInputMapFn;
	}

	public MapFn<T, Object> getOutputMapFn() {
		return baseOutputMapFn;
	}

	@Override
	public Converter getConverter() {
		return AVRO_CONVERTER;
	}

	@Override
	public SourceTarget<T> getDefaultFileSource(Path path) {
		return new AvroFileSourceTarget<T>(path, this);
	}

  private AvroDeepCopier<T> getDeepCopier() {
    if (deepCopier == null) {
      if (isSpecific()) {
        deepCopier = new AvroDeepCopier.AvroSpecificDeepCopier<T>(typeClass, getSchema());
      } else if (isGeneric()) {
        deepCopier = (AvroDeepCopier<T>) new AvroDeepCopier.AvroGenericDeepCopier(getSchema());
      } else {
        deepCopier = new AvroDeepCopier.AvroReflectDeepCopier<T>(typeClass, getSchema());
      }
    }
    return deepCopier;
  }

  public T getDetachedValue(T value) {
    if (this.baseInputMapFn instanceof IdentityFn && !Avros.isPrimitive(this)) {
      return getDeepCopier().deepCopy(value);
    }
    return value;
  }

	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof AvroType)) {
			return false;
		}
		AvroType at = (AvroType) other;
		return (typeClass.equals(at.typeClass) && subTypes.equals(at.subTypes));

	}

	@Override
	public int hashCode() {
		HashCodeBuilder hcb = new HashCodeBuilder();
		hcb.append(typeClass).append(subTypes);
		return hcb.toHashCode();
	}

}
