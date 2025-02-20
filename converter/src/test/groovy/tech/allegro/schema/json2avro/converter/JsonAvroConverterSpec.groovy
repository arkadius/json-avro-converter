package tech.allegro.schema.json2avro.converter

import groovy.json.JsonSlurper
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import spock.lang.Specification

import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Unroll
import tech.allegro.schema.json2avro.converter.types.AvroTypeConverter

import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.ZoneOffset;


class JsonAvroConverterSpec extends Specification {

    def converter = new JsonAvroConverter(new ObjectMapper(), 
        {name, value, path -> println "Unknown field $path with value $value"})
    def converterFailOnUnknown = new JsonAvroConverter(new ObjectMapper(), new FailOnUnknownField())
    def slurper = new JsonSlurper()

    def "should convert record with primitives"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_integer",
                    "type" : "int"
                  },
                  {
                    "name" : "field_long",
                    "type" : "long"
                  },
                  {
                    "name" : "field_float",
                    "type" : "float"
                  },
                  {
                    "name" : "field_double",
                    "type" : "double"
                  },
                  {
                    "name" : "field_boolean",
                    "type" : "boolean"
                  },
                  {
                    "name" : "field_string",
                    "type" : "string"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_integer": 1,
            "field_long": 2,
            "field_float": 1.1,
            "field_double": 1.2,
            "field_boolean": true,
            "field_string": "foobar"
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert bytes generic record"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_bytes",
                    "type" : "bytes"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_bytes": "\\u0001\\u0002\\u0003"
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should throw exception when parsing record with mismatched primitives"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_integer",
                    "type" : "int"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_integer": "foobar"
        }
        '''

        when:
        converter.convertToAvro(json.bytes, schema)

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Field field_integer is expected to be type: java.lang.Number"
    }

    @Unroll
    def "should ignore unknown fields"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_integer": 1,
            "field_long": 2,
            "field_float": 1.1,
            "field_double": 1.2,
            "field_boolean": true,
            "field_string": "foobar"
        }
        '''

        when:
        byte[] avro = converterNotFailingOnUnknown.convertToAvro(json.bytes, schema)

        then:
        def result = toMap(converterNotFailingOnUnknown.convertToJson(avro, schema))
        result.field_string == "foobar"
        result.keySet().size() == 1

        where:
        converterNotFailingOnUnknown << [new JsonAvroConverter(), new JsonAvroConverter(new ObjectMapper())]
    }
    
    def "should fail unknown fields"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_integer": 1,
            "field_long": 2,
            "field_float": 1.1,
            "field_double": 1.2,
            "field_boolean": true,
            "field_string": "foobar"
        }
        '''

        when:
        converterFailOnUnknown.convertToAvro(json.bytes, schema)

        then:
        thrown AvroConversionException
    }

    def "should throw exception when field is missing"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_integer",
                    "type" : "int"
                  },
                  {
                    "name" : "field_long",
                    "type" : "long"
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_integer": 1
        }
        '''

        when:
        converter.convertToAvro(json.bytes, schema)

        then:
        thrown AvroConversionException
    }

    def "should convert message with nested records"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_record",
                    "type" : {
                        "name" : "string_type",
                        "type": "record",
                        "fields": [
                            {
                                "type": "string",
                                "name": "field_string"
                            }
                        ]
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_record": {
                "field_string": "foobar"
            }
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested record with missing field"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_record",
                    "type" : {
                        "name" : "string_type",
                        "type": "record",
                        "fields": [
                            {
                                "type": "string",
                                "name": "field_string"
                            }
                        ]
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_foobar": 1
        }
        '''

        when:
        converter.convertToAvro(json.bytes, schema)

        then:
        thrown AvroConversionException
    }

    def "should convert nested map of primitives"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_map",
                    "type" : {
                        "name" : "map_type",
                        "type": "map",
                        "values": "string"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_map": {
                "foo": "bar"
            }
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should fail when converting nested map with mismatched value type"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_map",
                    "type" : {
                        "name" : "map_type",
                        "type": "map",
                        "values": "string"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_map": 1
        }
        '''

        when:
        converter.convertToAvro(json.bytes, schema)

        then:
        thrown AvroConversionException
    }

    def "should convert nested map of records"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_map",
                    "type" : {
                        "name" : "map_type",
                        "type": "map",
                        "values": {
                            "name" : "string_type",
                            "type": "record",
                            "fields": [
                                {
                                    "type": "string",
                                    "name": "field_string"
                                }
                            ]
                        }
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_map": {
                "foo": {
                    "field_string": "foobar"
                }
            }
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested array of primitives"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_array",
                    "type" : {
                        "name" : "array_type",
                        "type": "array",
                        "items": {
                            "name": "item",
                            "type": "string"
                        }
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_array": ["foo", "bar"]
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested array of records"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_array",
                    "type" : {
                        "name" : "array_type",
                        "type": "array",
                        "items": {
                            "name" : "string_type",
                            "type": "record",
                            "fields": [
                                {
                                    "type": "string",
                                    "name": "field_string"
                                }
                            ]
                        }
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_array": [
                {
                    "field_string": "foo"
                },
                {
                    "field_string": "bar"
                }
            ]
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested union of primitives"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_union",
                    "type" : ["string", "int"]
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_union": 8
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested union of records"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_union",
                    "type" : [
                        {
                            "name" : "int_type",
                            "type": "record",
                            "fields": [
                                {
                                    "type": "int",
                                    "name": "field_int"
                                }
                            ]
                        },
                        {
                            "name" : "string_type",
                            "type": "record",
                            "fields": [
                                {
                                    "type": "string",
                                    "name": "field_string"
                                }
                            ]
                        }
                    ]
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_union": {
                "field_string": "foobar"
            }
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        toMap(json) == toMap(converter.convertToJson(avro, schema))
    }

    def "should convert nested union with null and primitive should result in an optional field"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  },
                  {
                    "name" : "field_union",
                    "type" : ["null", "int"],
                    "default": null
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_string": "foobar"
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        def result = toMap(converter.convertToJson(avro, schema))
        result.field_string == toMap(json).field_string
        result.field_union == null
    }

    def "should convert nested union with null and record should result in an optional field"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  },
                  {
                    "name" : "field_union",
                    "type" : [
                        "null",
                        {
                            "name" : "int_type",
                            "type": "record",
                            "fields": [
                                {
                                    "type": "int",
                                    "name": "field_int"
                                }
                            ]
                        }
                    ],
                    "default": null
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_string": "foobar"
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        def result = toMap(converter.convertToJson(avro, schema))
        result.field_string == toMap(json).field_string
        result.field_union == null
    }

    def "should convert nested union with null and map should result in an optional field"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  },
                  {
                    "name" : "field_union",
                    "type" : [
                        "null",
                        {
                            "name" : "map_type",
                            "type": "map",
                            "values": {
                                "name" : "string_type",
                                "type": "record",
                                "fields": [
                                    {
                                        "type": "string",
                                        "name": "field_string"
                                    }
                                ]
                            }
                        }
                    ],
                    "default": null
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_string": "foobar"
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        def result = toMap(converter.convertToJson(avro, schema))
        result.field_string == toMap(json).field_string
        result.field_union == null
    }

    def "should convert optional fields should not be wrapped when converting from avro to json"() {
        given:
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "field_string",
                    "type" : "string"
                  },
                  {
                    "name" : "field_union",
                    "type" : ["null", "int"],
                    "default": null
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_string": "foobar",
            "field_union": 1
        }
        '''

        when:
        def result = converter.convertToJson(converter.convertToAvro(json.bytes, schema), schema)

        then:
        toMap(result) == toMap(json)
    }

    def "should print full path to invalid field on error"() {
        given:
        def schema = '''
            {
              "name": "testSchema",
              "type": "record",
              "fields": [
                {
                  "name": "field",
                  "default": null,
                  "type": [
                    "null",
                    {
                      "name": "field_type",
                      "type": "record",
                      "fields": [
                        {
                          "name": "stringValue",
                          "type": "string"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
        '''

        def json = '''
        {
            "field": { "stringValue": 1 }
        }
        '''

        when:
        converter.convertToJson(converter.convertToAvro(json.bytes, schema), schema)

        then:
        def exception = thrown(AvroConversionException)
        exception.cause.message  ==~ /.*field\.stringValue.*/
    }

    def "should parse enum types properly"() {
        given:
        def schema = '''
            {
              "name": "testSchema",
              "type": "record",
              "fields": [
                  {
                    "name" : "field_enum",
                    "type" : {
                        "name" : "MyEnums",
                        "type" : "enum",
                        "symbols" : [ "A", "B", "C" ]
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_enum": "A"
        }
        '''

        when:
        def result = converter.convertToJson(converter.convertToAvro(json.bytes, schema), schema)

        then:
        toMap(result) == toMap(json)
    }

    def "should throw the appropriate error when passing an invalid enum type"() {
        given:
        def schema = '''
            {
              "name": "testSchema",
              "type": "record",
              "fields": [
                  {
                    "name" : "field_enum",
                    "type" : {
                        "name" : "MyEnums",
                        "type" : "enum",
                        "symbols" : [ "A", "B", "C" ]
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "field_enum": "D"
        }
        '''

        when:
        converter.convertToJson(converter.convertToAvro(json.bytes, schema), schema)

        then:
        def exception = thrown(AvroConversionException)
        exception.cause.message  ==~ /.*enum type and be one of A, B, C.*/
    }

    def "should accept null when value can be of any nullable array/map type"() {
        given:
        def schema = '''
            {
                "type": "record",
                "name": "testSchema",
                "fields": [
                    {
                        "name": "payload",
                        "type": [
                            "null",
                            {
                                "type": "array",
                                "items": "string"
                            },
                            {
                                "type": "map",
                                "values": [
                                    "null",
                                    "string",
                                    "int"
                                ]
                             }
                        ]
                    }
                ]
            }
        '''

        def json = '''
        {
            "payload": {
                "foo": null
            }
        }
        '''

        when:
        byte[] avro = converter.convertToAvro(json.bytes, schema)

        then:
        !toMap(converter.convertToJson(avro, schema)).payload.foo
    }

    def "should convert iso date time to timestamp-millis"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": "2022-02-05T16:20:29Z"
        }
        '''

        when:
        GenericData.Record record = converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        LocalDateTime.of(2022, 02, 05, 16, 20, 29).toEpochSecond(ZoneOffset.UTC) * 1000 == record.get("datetime")
    }

    def "should convert long time to timestamp-millis"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": 1644078029000
        }
        '''

        when:
        GenericData.Record record = converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        1644078029000 == record.get("datetime")
    }

    def "should fail if date time is malformed"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": "test"
        }
        '''

        when:
        converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Field datetime should be a valid date time."
    }

    def "should fail if date is not a String nor a Long"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": {}
        }
        '''

        when:
        converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Field datetime is expected to be type: java.lang.String or java.lang.Number."
    }

    def "should allow a timestamp-millis to be nullable"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : [{
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }, "null"]
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": null
        }
        '''

        when:
        GenericData.Record record = converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        null == record.get("datetime")
    }

    def "should throw error when timestamp-millis malformed on union type"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "datetime",
                    "type" : [{
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }, "null"]
                  }
              ]
            }
        '''

        def json = '''
        {
            "datetime": "test"
        }
        '''

        when:
        converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Could not evaluate union, field datetime is expected to be one of these: date time string, timestamp number, NULL. If this is a complex type, check if offending field: datetime adheres to schema."
    }

    def "should convert json numeric to avro decimal"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "byteDecimal",
                    "type" : {
                      "type" : "bytes",
                      "logicalType" : "decimal",
                      "precision": 15, 
                      "scale": 5
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "byteDecimal": 123.456
        }
        '''

        when:
        GenericData.Record record = converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        new BigDecimal("123.45600") == new BigDecimal(new BigInteger(((ByteBuffer) record.get("byteDecimal")).array()), 5)
    }

    def "should convert json string to avro decimal"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "byteDecimal",
                    "type" : {
                      "type" : "bytes",
                      "logicalType" : "decimal",
                      "precision": 15, 
                      "scale": 5
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "byteDecimal": "123.456"
        }
        '''

        when:
        GenericData.Record record = converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        new BigDecimal("123.45600") == new BigDecimal(new BigInteger(((ByteBuffer) record.get("byteDecimal")).array()), 5)
    }

    def "should throw error when decimal json string is not a number"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "byteDecimal",
                    "type" : {
                      "type" : "bytes",
                      "logicalType" : "decimal",
                      "precision": 15, 
                      "scale": 5
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "byteDecimal": "test"
        }
        '''

        when:
        converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Field byteDecimal is expected to be a valid number. current value is test."
    }

    def "should throw error when decimal is malformed on a union type"() {
        given:
        def converter = new JsonAvroConverter()
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "byteDecimal",
                    "type" : [{
                      "type" : "bytes",
                      "logicalType" : "decimal",
                      "precision": 15, 
                      "scale": 5
                    }, "null"]
                  }
              ]
            }
        '''

        def json = '''
        {
            "byteDecimal": "test"
        }
        '''

        when:
        converter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        def e = thrown AvroConversionException
        e.message == "Failed to convert JSON to Avro: Could not evaluate union, field byteDecimal is expected to be one of these: string number, decimal, NULL. If this is a complex type, check if offending field: byteDecimal adheres to schema."
    }

    def "should convert specific record"() {
        given:
        def json = '''
        {
            "test": "test",
            "enumTest": "s1"
        }
        '''
        def clazz = SpecificRecordConvertTest.class
        def schema = SpecificRecordConvertTest.getClassSchema()

        when:
        SpecificRecordConvertTest result = converter.convertToSpecificRecord(json.bytes, clazz, schema)
        then:
        result != null && result instanceof SpecificRecordConvertTest && result.getTest() == "test"
    }

    def "should convert specific record and back to json"() {
        given:
        def json = '''{"test":"test","enumTest":"s1"}'''
        def clazz = SpecificRecordConvertTest.class
        def schema = SpecificRecordConvertTest.getClassSchema()

        when:
        SpecificRecordConvertTest record = converter.convertToSpecificRecord(json.bytes, clazz, schema)
        def result = converter.convertToJson(record)
        then:
        result != null && new String(result) == json
    }

    def "should allow to customize the avro type conversion for a logical-type"() {
        given:
        def now = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        def additionalConverter = new AvroTypeConverter() {
            @Override
            Object convert(Schema.Field field, Schema schema, Object jsonValue, Deque<String> path, boolean silently) {
                return now;
            }

            @Override
            boolean canManage(Schema schema, Deque<String> path) {
                return schema.getType() == Schema.Type.LONG && schema.getLogicalType().name == "timestamp-millis"
            }
        }

        def converterWithCustomConverter = new JsonAvroConverter(new ObjectMapper(), new CompositeJsonToAvroReader([additionalConverter]))
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "customDate",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "customDate": "now"
        }
        '''

        when:
        GenericData.Record record = converterWithCustomConverter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        now == record.get("customDate")
    }

    def "should allow to customize the avro type conversion for a field"() {
        given:
        def additionalConverter = new AvroTypeConverter() {
            @Override
            Object convert(Schema.Field field, Schema schema, Object jsonValue, Deque<String> path, boolean silently) {
                return "custom-" + jsonValue;
            }

            @Override
            boolean canManage(Schema schema, Deque<String> path) {
                return path.getLast() == "customString"
            }
        }

        def converterWithCustomConverter = new JsonAvroConverter(new CompositeJsonToAvroReader(additionalConverter))
        def schema = '''
            {
              "type" : "record",
              "name" : "testSchema",
              "fields" : [
                  {
                    "name" : "customString",
                    "type" : {
                      "type" : "long",
                      "logicalType" : "timestamp-millis"
                    }
                  }
              ]
            }
        '''

        def json = '''
        {
            "customString": "foo"
        }
        '''

        when:
        GenericData.Record record = converterWithCustomConverter.convertToGenericDataRecord(json.bytes, new Schema.Parser().parse(schema))

        then:
        "custom-foo" == record.get("customString")
    }

    def toMap(String json) {
        slurper.parseText(json)
    }

    def toMap(byte[] json) {
        slurper.parse(json)
    }
}
