/*
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package com.ing.data.cassandra.jdbc;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.data.UdtValue;
import com.datastax.oss.driver.api.core.metadata.schema.FunctionMetadata;
import com.datastax.oss.driver.api.core.metadata.schema.FunctionSignature;
import com.datastax.oss.driver.api.core.metadata.schema.KeyspaceMetadata;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.ing.data.cassandra.jdbc.metadata.MetadataResultSet;
import com.ing.data.cassandra.jdbc.metadata.MetadataRow;
import com.ing.data.cassandra.jdbc.types.AbstractJdbcType;
import com.ing.data.cassandra.jdbc.types.DataTypeEnum;
import org.apache.commons.lang3.StringUtils;

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.ing.data.cassandra.jdbc.types.AbstractJdbcType.DEFAULT_SCALE;
import static com.ing.data.cassandra.jdbc.types.TypesMap.getTypeForComparator;
import static java.sql.DatabaseMetaData.functionColumnIn;
import static java.sql.DatabaseMetaData.functionReturn;
import static java.sql.DatabaseMetaData.typeNullable;
import static java.sql.DatabaseMetaData.typePredBasic;
import static java.sql.Types.JAVA_OBJECT;

/**
 * Utility class to manage database metadata result sets ({@link CassandraMetadataResultSet} objects).
 */
// TODO: split by families of metadata (table, columns, functions, ...) and move to metadata package.
public final class MetadataResultSets {
    /**
     * Gets an instance of {@code MetadataResultSets}.
     */
    public static final MetadataResultSets INSTANCE = new MetadataResultSets();

    static final String AUTO_INCREMENT = "AUTO_INCREMENT";
    static final String BASE_TYPE = "BASE_TYPE";
    static final String CASE_SENSITIVE = "CASE_SENSITIVE";
    static final String CHAR_OCTET_LENGTH = "CHAR_OCTET_LENGTH";
    static final String CLASS_NAME = "CLASS_NAME";
    static final String COLUMN_NAME = "COLUMN_NAME";
    static final String COLUMN_TYPE = "COLUMN_TYPE";
    static final String CREATE_PARAMS = "CREATE_PARAMS";
    static final String DATA_TYPE = "DATA_TYPE";
    static final String FIXED_PRECISION_SCALE = "FIXED_PREC_SCALE";
    static final String FUNCTION_CATALOG = "FUNCTION_CAT";
    static final String FUNCTION_NAME = "FUNCTION_NAME";
    static final String FUNCTION_SCHEMA = "FUNCTION_SCHEM";
    static final String FUNCTION_TYPE = "FUNCTION_TYPE";
    static final String IS_NULLABLE = "IS_NULLABLE";
    static final String LENGTH = "LENGTH";
    static final String LITERAL_PREFIX = "LITERAL_PREFIX";
    static final String LITERAL_SUFFIX = "LITERAL_SUFFIX";
    static final String LOCALIZED_TYPE_NAME = "LOCAL_TYPE_NAME";
    static final String MAXIMUM_SCALE = "MAXIMUM_SCALE";
    static final String MINIMUM_SCALE = "MINIMUM_SCALE";
    static final String NULLABLE = "NULLABLE";
    static final String NUM_PRECISION_RADIX = "NUM_PREC_RADIX";
    static final String ORDINAL_POSITION = "ORDINAL_POSITION";
    static final String PRECISION = "PRECISION";
    static final String RADIX = "RADIX";
    static final String REMARKS = "REMARKS";
    static final String SCALE = "SCALE";
    static final String SEARCHABLE = "SEARCHABLE";
    static final String SPECIFIC_NAME = "SPECIFIC_NAME";
    static final String SQL_DATA_TYPE = "SQL_DATA_TYPE";
    static final String SQL_DATETIME_SUB = "SQL_DATETIME_SUB";
    static final String TABLE = "TABLE";
    static final String TYPE_CATALOG = "TYPE_CAT";
    static final String TYPE_NAME = "TYPE_NAME";
    static final String TYPE_SCHEMA = "TYPE_SCHEM";
    static final String UNSIGNED_ATTRIBUTE = "UNSIGNED_ATTRIBUTE";
    static final String WILDCARD_CHAR = "%";
    static final String YES_VALUE = "YES";


    private MetadataResultSets() {
        // Private constructor to hide the public one.
    }

    /**
     * Builds a valid result set of the description of the user-defined types (UDTs) defined in a particular schema.
     * This method is used to implement the method {@link DatabaseMetaData#getUDTs(String, String, String, int[])}.
     * <p>
     * Schema-specific UDTs in a Cassandra database will be considered as having type {@code JAVA_OBJECT}.
     * </p>
     * <p>
     * Only types matching the catalog, schema, type name and type criteria are returned. They are ordered by
     * {@code DATA_TYPE}, {@code TYPE_CAT}, {@code TYPE_SCHEM} and {@code TYPE_NAME}. The type name parameter may be
     * a fully-qualified name (it should respect the format {@code <SCHEMA_NAME>.<TYPE_NAME>}). In this case, the
     * {@code catalog} and {@code schemaPattern} parameters are ignored.
     * </p>
     * <p>
     * The columns of this result set are:
     * <ol>
     *     <li><b>TYPE_CAT</b> String => type's catalog, may be {@code null}: here is the Cassandra cluster name
     *     (if available).</li>
     *     <li><b>TYPE_SCHEM</b> String => type's schema, may be {@code null}: here is the keyspace the type is
     *     member of.</li>
     *     <li><b>TYPE_NAME</b> String => user-defined type name.</li>
     *     <li><b>CLASS_NAME</b> String => Java class name, always {@link UdtValue} in the current implementation.</li>
     *     <li><b>DATA_TYPE</b> int => type value defined in {@link Types}. One of {@link Types#JAVA_OBJECT},
     *     {@link Types#STRUCT}, or {@link Types#DISTINCT}. Always {@link Types#JAVA_OBJECT} in the current
     *     implementation.</li>
     *     <li><b>REMARKS</b> String => explanatory comment on the type, always empty in the current
     *     implementation.</li>
     *     <li><b>BASE_TYPE</b> short => type code of the source type of a {@code DISTINCT} type or the type that
     *     implements the user-generated reference type of the {@code SELF_REFERENCING_COLUMN} of a structured type
     *     as defined in {@link Types} ({@code null} if {@code DATA_TYPE} is not {@code DISTINCT} or not
     *     {@code STRUCT} with {@code REFERENCE_GENERATION = USER_DEFINED}). Always {@code null} in the current
     *     implementation.</li>
     * </ol>
     * </p>
     *
     * @param statement       The statement.
     * @param schemaPattern   A schema pattern name; must match the schema name as it is stored in the database;
     *                        {@code ""} retrieves those without a schema (will always return an empty set);
     *                        {@code null} means that the schema name should not be used to narrow the search and in
     *                        this case the search is restricted to the current schema (if available).
     * @param typeNamePattern A type name pattern; must match the type name as it is stored in the database (not
     *                        case-sensitive); may be a fully qualified name.
     * @param types           A list of user-defined types ({@link Types#JAVA_OBJECT}, {@link Types#STRUCT}, or
     *                        {@link Types#DISTINCT}) to include; {@code null} returns all types. All the UDTs defined
     *                        in a Cassandra database are considered as {@link Types#JAVA_OBJECT}, so other values will
     *                        return an empty result set.
     * @return A valid result set for implementation of {@link DatabaseMetaData#getUDTs(String, String, String, int[])}.
     * @throws SQLException when something went wrong during the creation of the result set.
     */
    public CassandraMetadataResultSet makeUDTs(final CassandraStatement statement, final String schemaPattern,
                                               final String typeNamePattern, final int[] types) throws SQLException {
        final ArrayList<MetadataRow> udtsRows = new ArrayList<>();
        final Map<CqlIdentifier, KeyspaceMetadata> keyspaces = statement.connection.getClusterMetadata().getKeyspaces();

        // Parse the fully-qualified type name, if necessary.
        String schemaName = schemaPattern;
        String typeName = typeNamePattern;
        if (typeNamePattern.contains(".")) {
            final String[] fullyQualifiedTypeNameParts = typeNamePattern.split("\\.");
            schemaName = fullyQualifiedTypeNameParts[0];
            typeName = fullyQualifiedTypeNameParts[1];
        }

        for (final Map.Entry<CqlIdentifier, KeyspaceMetadata> keyspace : keyspaces.entrySet()) {
            final KeyspaceMetadata keyspaceMetadata = keyspace.getValue();
            if (StringUtils.isEmpty(schemaName) || schemaName.equals(keyspaceMetadata.getName().asInternal())) {
                final Map<CqlIdentifier, UserDefinedType> udts = keyspaceMetadata.getUserDefinedTypes();

                for (final Map.Entry<CqlIdentifier, UserDefinedType> udt : udts.entrySet()) {
                    final UserDefinedType udtMetadata = udt.getValue();
                    if (typeName.equalsIgnoreCase(udtMetadata.getName().asInternal())
                        && (types == null || Arrays.stream(types).anyMatch(type -> type == JAVA_OBJECT))) {
                        final MetadataRow row = new MetadataRow()
                            .addEntry(TYPE_CATALOG, statement.connection.getCatalog())
                            .addEntry(TYPE_SCHEMA, keyspaceMetadata.getName().asInternal())
                            .addEntry(TYPE_NAME, udtMetadata.getName().asInternal())
                            .addEntry(CLASS_NAME, UdtValue.class.getName())
                            .addEntry(DATA_TYPE, String.valueOf(JAVA_OBJECT))
                            .addEntry(REMARKS, StringUtils.EMPTY)
                            .addEntry(BASE_TYPE, null);
                        udtsRows.add(row);
                    }
                }
            }
        }
        // Results should all have the same DATA_TYPE and TYPE_CAT so just sort them by TYPE_SCHEM then TYPE_NAME.
        udtsRows.sort(Comparator.comparing(row -> ((MetadataRow) row).getString(TYPE_SCHEMA))
            .thenComparing(row -> ((MetadataRow) row).getString(TYPE_NAME)));
        return CassandraMetadataResultSet.buildFrom(statement, new MetadataResultSet().setRows(udtsRows));
    }

    /**
     * Builds a valid result set of all the data types supported by this database. This method is used to implement
     * the method {@link DatabaseMetaData#getTypeInfo()}.
     * <p>
     * They are ordered by DATA_TYPE and then by how closely the data type maps to the corresponding JDBC SQL type.
     * </p>
     * <p>
     * The Cassandra database does not support SQL distinct types. The information on the individual structured types
     * (considered as {@link Types#JAVA_OBJECT}, not {@link Types#STRUCT}) may be obtained from the {@code getUDTs()}
     * method.
     * </p>
     * <p>
     * The columns of this result set are:
     *     <ol>
     *         <li><b>TYPE_NAME</b> String => type name.</li>
     *         <li><b>DATA_TYPE</b> int => SQL data type from {@link Types}.</li>
     *         <li><b>PRECISION</b> int => maximum precision.</li>
     *         <li><b>LITERAL_PREFIX</b> String => prefix used to quote a literal (may be {@code null}).</li>
     *         <li><b>LITERAL_SUFFIX</b> String => suffix used to quote a literal (may be {@code null}).</li>
     *         <li><b>CREATE_PARAMS</b> String => parameters used in creating the type (may be {@code null}).</li>
     *         <li><b>NULLABLE</b> short => can you use {@code NULL} for this type:
     *              <ul>
     *                  <li>{@link DatabaseMetaData#typeNoNulls} - does not allow {@code NULL} values</li>
     *                  <li>{@link DatabaseMetaData#typeNullable} - allows {@code NULL} values</li>
     *                  <li>{@link DatabaseMetaData#typeNullableUnknown} - nullability unknown</li>
     *              </ul>
     *         </li>
     *         <li><b>CASE_SENSITIVE</b> boolean => is it case sensitive.</li>
     *         <li><b>SEARCHABLE</b> short => can you use "{@code WHERE}" based on this type:
     *              <ul>
     *                  <li>{@link DatabaseMetaData#typePredNone} - no support</li>
     *                  <li>{@link DatabaseMetaData#typePredChar} - only supported with {@code WHERE .. LIKE}</li>
     *                  <li>{@link DatabaseMetaData#typePredBasic} - supported except for {@code WHERE .. LIKE}</li>
     *                  <li>{@link DatabaseMetaData#typeSearchable} - supported for all {@code WHERE ..}</li>
     *              </ul>
     *         </li>
     *         <li><b>UNSIGNED_ATTRIBUTE</b> boolean => is it unsigned.</li>
     *         <li><b>FIXED_PREC_SCALE</b> boolean => can it be a money value.</li>
     *         <li><b>AUTO_INCREMENT</b> boolean => can it be used for an auto-increment value. Always {@code false}
     *         since Cassandra does not support auto-increment.</li>
     *         <li><b>LOCAL_TYPE_NAME</b> String => localized version of type name (may be {@code null}).</li>
     *         <li><b>MINIMUM_SCALE</b> short => minimum scale supported.</li>
     *         <li><b>MAXIMUM_SCALE</b> short => maximum scale supported.</li>
     *         <li><b>SQL_DATA_TYPE</b> int => not used.</li>
     *         <li><b>SQL_DATETIME_SUB</b> int => not used.</li>
     *         <li><b>NUM_PREC_RADIX</b> String => precision radix (typically either 10 or 2).</li>
     *     </ol>
     * </p>
     * <p>
     * The {@code PRECISION} column represents the maximum column size that the server supports for the given datatype.
     * For numeric data, this is the maximum precision. For character data, this is the length in characters. For
     * datetime data types, this is the length in characters of the {@code String} representation (assuming the maximum
     * allowed precision of the fractional seconds component). For binary data, this is the length in bytes.
     * For the {@code ROWID} datatype (not supported by Cassandra), this is the length in bytes. The value {@code null}
     * is returned for data types where the column size is not applicable.
     * </p>
     *
     * @param statement The statement.
     * @return A valid result set for implementation of {@link DatabaseMetaData#getTypeInfo()}.
     * @throws SQLException when something went wrong during the creation of the result set.
     */
    public CassandraMetadataResultSet makeTypes(final CassandraStatement statement) throws SQLException {
        final ArrayList<MetadataRow> types = new ArrayList<>();
        for (final DataTypeEnum dataType : DataTypeEnum.values()) {
            final AbstractJdbcType<?> jdbcType = getTypeForComparator(dataType.asLowercaseCql());
            String literalQuotingSymbol = null;
            if (jdbcType.needsQuotes()) {
                literalQuotingSymbol = "'";
            }
            final MetadataRow row = new MetadataRow()
                .addEntry(TYPE_NAME, dataType.cqlType)
                .addEntry(DATA_TYPE, String.valueOf(jdbcType.getJdbcType()))
                .addEntry(PRECISION, String.valueOf(jdbcType.getPrecision(null)))
                .addEntry(LITERAL_PREFIX, literalQuotingSymbol)
                .addEntry(LITERAL_SUFFIX, literalQuotingSymbol)
                .addEntry(CREATE_PARAMS, null)
                .addEntry(NULLABLE, String.valueOf(typeNullable)) // absence is the equivalent of null in Cassandra
                .addEntry(CASE_SENSITIVE, String.valueOf(jdbcType.isCaseSensitive()))
                .addEntry(SEARCHABLE, String.valueOf(typePredBasic))
                .addEntry(UNSIGNED_ATTRIBUTE, String.valueOf(!jdbcType.isSigned()))
                .addEntry(FIXED_PRECISION_SCALE, String.valueOf(!jdbcType.isCurrency()))
                .addEntry(AUTO_INCREMENT, String.valueOf(false))
                .addEntry(LOCALIZED_TYPE_NAME, null)
                .addEntry(MINIMUM_SCALE, String.valueOf(DEFAULT_SCALE))
                .addEntry(MAXIMUM_SCALE, String.valueOf(jdbcType.getScale(null)))
                .addEntry(SQL_DATA_TYPE, null)
                .addEntry(SQL_DATETIME_SUB, null)
                .addEntry(NUM_PRECISION_RADIX, String.valueOf(jdbcType.getPrecision(null)));
            types.add(row);
        }
        // Sort results by DATA_TYPE.
        types.sort(Comparator.comparing(row -> Integer.valueOf(row.getString(DATA_TYPE))));
        return CassandraMetadataResultSet.buildFrom(statement, new MetadataResultSet().setRows(types));
    }

    /**
     * Builds a valid result set of the system and user functions available in the given catalog (Cassandra cluster).
     * This method is used to implement the method {@link DatabaseMetaData#getFunctions(String, String, String)}.
     * <p>
     * Only system and user function descriptions matching the schema and function name criteria are returned. They are
     * ordered by {@code FUNCTION_CAT}, {@code FUNCTION_SCHEM}, {@code FUNCTION_NAME} and {@code SPECIFIC_NAME}.
     * </p>
     * <p>
     * The columns of this result set are:
     *     <ol>
     *         <li><b>FUNCTION_CAT</b> String => function catalog, may be {@code null}: here is the Cassandra cluster
     *         name (if available).</li>
     *         <li><b>FUNCTION_SCHEM</b> String => function schema, may be {@code null}: here is the keyspace the table
     *         is member of.</li>
     *         <li><b>FUNCTION_NAME</b> String => function name. This is the name used to invoke the function.</li>
     *         <li><b>REMARKS</b> String => explanatory comment on the function (always empty, Cassandra does not
     *         allow to describe functions with a comment).</li>
     *         <li><b>FUNCTION_TYPE</b> short => kind of function:
     *             <ul>
     *                 <li>{@link DatabaseMetaData#functionResultUnknown} - cannot determine if a return value or table
     *                 will be returned</li>
     *                 <li>{@link DatabaseMetaData#functionNoTable} - does not return a table (Cassandra user-defined
     *                 functions only return CQL types, so never a table)</li>
     *                 <li>{@link DatabaseMetaData#functionReturnsTable} - returns a table</li>
     *             </ul>
     *         </li>
     *         <li><b>SPECIFIC_NAME</b> String => the name which uniquely identifies this function within its schema.
     *         This is a user specified, or DBMS generated, name that may be different then the {@code FUNCTION_NAME}
     *         for example with overload functions.</li>
     *     </ol>
     * </p>
     * <p>
     * A user may not have permission to execute any of the functions that are returned by {@code getFunctions}.
     * </p>
     *
     * @param statement             The statement.
     * @param schemaPattern         A schema name pattern. It must match the schema name as it is stored in the
     *                              database; {@code ""} retrieves those without a schema and {@code null} means that
     *                              the schema name should not be used to narrow down the search.
     * @param functionNamePattern   A function name pattern; must match the function name as it is stored in the
     *                              database.
     * @return A valid result set for implementation of {@link DatabaseMetaData#getFunctions(String, String, String)}.
     * @throws SQLException when something went wrong during the creation of the result set.
     */
    public CassandraMetadataResultSet makeFunctions(final CassandraStatement statement, final String schemaPattern,
                                                    final String functionNamePattern) throws SQLException {
        final ArrayList<MetadataRow> functionsRows = new ArrayList<>();
        final Map<CqlIdentifier, KeyspaceMetadata> keyspaces = statement.connection.getClusterMetadata().getKeyspaces();

        for (final Map.Entry<CqlIdentifier, KeyspaceMetadata> keyspace : keyspaces.entrySet()) {
            final KeyspaceMetadata keyspaceMetadata = keyspace.getValue();
            String schemaNamePattern = schemaPattern;
            if (WILDCARD_CHAR.equals(schemaPattern)) {
                schemaNamePattern = keyspaceMetadata.getName().asInternal();
            }
            if (schemaNamePattern == null || schemaNamePattern.equals(keyspaceMetadata.getName().asInternal())) {
                final Map<FunctionSignature, FunctionMetadata> functions = keyspaceMetadata.getFunctions();

                for (final FunctionSignature function : functions.keySet()) {
                    if (WILDCARD_CHAR.equals(functionNamePattern) || functionNamePattern == null
                        || functionNamePattern.equals(function.getName().asInternal())) {
                        final MetadataRow row = new MetadataRow()
                            .addEntry(FUNCTION_CATALOG, statement.connection.getCatalog())
                            .addEntry(FUNCTION_SCHEMA, keyspaceMetadata.getName().asInternal())
                            .addEntry(FUNCTION_NAME, function.getName().asInternal())
                            .addEntry(REMARKS, StringUtils.EMPTY)
                            .addEntry(FUNCTION_TYPE, String.valueOf(DatabaseMetaData.functionNoTable))
                            .addEntry(SPECIFIC_NAME, function.getName().asInternal());
                        functionsRows.add(row);
                    }
                }
            }
        }

        // Results should all have the same FUNCTION_CAT, so just sort them by FUNCTION_SCHEM then FUNCTION_NAME (since
        // here SPECIFIC_NAME is equal to FUNCTION_NAME).
        functionsRows.sort(Comparator.comparing(row -> ((MetadataRow) row).getString(FUNCTION_SCHEMA))
            .thenComparing(row -> ((MetadataRow) row).getString(FUNCTION_NAME)));
        return CassandraMetadataResultSet.buildFrom(statement, new MetadataResultSet().setRows(functionsRows));
    }

    /**
     * Builds a valid result set of the given catalog's system or user function parameters and return type.
     * This method is used to implement the method
     * {@link DatabaseMetaData#getFunctionColumns(String, String, String, String)}.
     * <p>
     * Only descriptions matching the schema, function and parameter name criteria are returned. They are ordered by
     * {@code FUNCTION_CAT}, {@code FUNCTION_SCHEM}, {@code FUNCTION_NAME} and {@code SPECIFIC_NAME}. Within this, the
     * return value, if any, is first. Next are the parameter descriptions in call order. The column descriptions
     * follow in column number order.
     * </p>
     * <p>
     * The columns of this result set are:
     *     <ol>
     *         <li><b>FUNCTION_CAT</b> String => function catalog, may be {@code null}: here is the Cassandra cluster
     *         name (if available).</li>
     *         <li><b>FUNCTION_SCHEM</b> String => function schema, may be {@code null}: here is the keyspace the table
     *         is member of.</li>
     *         <li><b>FUNCTION_NAME</b> String => function name. This is the name used to invoke the function.</li>
     *         <li><b>COLUMN_NAME</b> String => column/parameter name.</li>
     *         <li><b>COLUMN_TYPE</b> short => kind of column/parameter:
     *             <ul>
     *                 <li>{@link DatabaseMetaData#functionColumnUnknown} - unknown type</li>
     *                 <li>{@link DatabaseMetaData#functionColumnIn} - {@code IN} parameter</li>
     *                 <li>{@link DatabaseMetaData#functionColumnInOut} - {@code INOUT} parameter</li>
     *                 <li>{@link DatabaseMetaData#functionColumnOut} - {@code OUT} parameter</li>
     *                 <li>{@link DatabaseMetaData#functionReturn} - function return value</li>
     *                 <li>{@link DatabaseMetaData#functionColumnResult} - indicates that the parameter or column is a
     *                 column in the {@code ResultSet}</li>
     *             </ul>
     *         </li>
     *         <li><b>DATA_TYPE</b> int => SQL data type from {@link Types}.</li>
     *         <li><b>TYPE_NAME</b> String => SQL type name, for a UDT type the type name is fully qualified.</li>
     *         <li><b>PRECISION</b> int => maximum precision.</li>
     *         <li><b>LENGTH</b> int => length in bytes of data.</li>
     *         <li><b>SCALE</b> int => scale, {@code null} is returned for data types where SCALE is not
     *         applicable.</li>
     *         <li><b>RADIX</b> short => precision radix.</li>
     *         <li><b>NULLABLE</b> short => can you use {@code NULL} for this type:
     *              <ul>
     *                  <li>{@link DatabaseMetaData#typeNoNulls} - does not allow {@code NULL} values</li>
     *                  <li>{@link DatabaseMetaData#typeNullable} - allows {@code NULL} values</li>
     *                  <li>{@link DatabaseMetaData#typeNullableUnknown} - nullability unknown</li>
     *              </ul>
     *         </li>
     *         <li><b>REMARKS</b> String => comment describing column/parameter (always empty, Cassandra does not
     *         allow to describe columns with a comment).</li>
     *         <li><b>CHAR_OCTET_LENGTH</b> int => the maximum length of binary and character based parameters or
     *         columns. For any other datatype the returned value is a {@code NULL}.</li>
     *         <li><b>ORDINAL_POSITION</b> int => the ordinal position, starting from 1, for the input and output
     *         parameters. A value of 0 is returned if this row describes the function's return value. For result set
     *         columns, it is the ordinal position of the column in the result set starting from 1.</li>
     *         <li><b>IS_NULLABLE</b> String => "YES" if a parameter or column accepts {@code NULL} values, "NO"
     *         if not and empty if the nullability is unknown.</li>
     *         <li><b>SPECIFIC_NAME</b> String => the name which uniquely identifies this function within its schema.
     *         This is a user specified, or DBMS generated, name that may be different then the {@code FUNCTION_NAME}
     *         for example with overload functions.</li>
     *     </ol>
     * </p>
     * <p>
     * The {@code PRECISION} column represents the maximum column size that the server supports for the given datatype.
     * For numeric data, this is the maximum precision. For character data, this is the length in characters. For
     * datetime data types, this is the length in characters of the {@code String} representation (assuming the maximum
     * allowed precision of the fractional seconds component). For binary data, this is the length in bytes.
     * For the {@code ROWID} datatype (not supported by Cassandra), this is the length in bytes. The value {@code null}
     * is returned for data types where the column size is not applicable.
     * </p>
     *
     * @param statement             The statement.
     * @param schemaPattern         A schema name pattern. It must match the schema name as it is stored in the
     *                              database; {@code ""} retrieves those without a schema and {@code null} means that
     *                              the schema name should not be used to narrow down the search.
     * @param functionNamePattern   A function name pattern; must match the function name as it is stored in the
     *                              database.
     * @param columnNamePattern     A parameter name pattern; must match the parameter or column name as it is stored
     *                              in the database.
     * @return A valid result set for implementation of
     * {@link DatabaseMetaData#getFunctionColumns(String, String, String, String)}.
     * @throws SQLException when something went wrong during the creation of the result set.
     */
    public CassandraMetadataResultSet makeFunctionColumns(final CassandraStatement statement,
                                                          final String schemaPattern,
                                                          final String functionNamePattern,
                                                          final String columnNamePattern) throws SQLException {
        final ArrayList<MetadataRow> functionParamsRows = new ArrayList<>();
        final Map<CqlIdentifier, KeyspaceMetadata> keyspaces = statement.connection.getClusterMetadata().getKeyspaces();

        for (final Map.Entry<CqlIdentifier, KeyspaceMetadata> keyspace : keyspaces.entrySet()) {
            final KeyspaceMetadata keyspaceMetadata = keyspace.getValue();
            String schemaNamePattern = schemaPattern;
            if (WILDCARD_CHAR.equals(schemaPattern)) {
                schemaNamePattern = keyspaceMetadata.getName().asInternal();
            }
            if (schemaNamePattern == null || schemaNamePattern.equals(keyspaceMetadata.getName().asInternal())) {
                final Map<FunctionSignature, FunctionMetadata> functions = keyspaceMetadata.getFunctions();

                for (final Map.Entry<FunctionSignature, FunctionMetadata> function : functions.entrySet()) {
                    final FunctionSignature functionSignature = function.getKey();
                    final FunctionMetadata functionMetadata = function.getValue();
                    if (WILDCARD_CHAR.equals(functionNamePattern) || functionNamePattern == null
                        || functionNamePattern.equals(functionSignature.getName().asInternal())) {
                        // Function return type.
                        final AbstractJdbcType<?> returnJdbcType =
                            getTypeForComparator(functionMetadata.getReturnType().asCql(false, true));
                        final MetadataRow row = new MetadataRow()
                            .addEntry(FUNCTION_CATALOG, statement.connection.getCatalog())
                            .addEntry(FUNCTION_SCHEMA, keyspaceMetadata.getName().asInternal())
                            .addEntry(FUNCTION_NAME, functionSignature.getName().asInternal())
                            .addEntry(COLUMN_NAME, StringUtils.EMPTY)
                            .addEntry(COLUMN_TYPE, String.valueOf(functionReturn))
                            .addEntry(DATA_TYPE, String.valueOf(returnJdbcType.getJdbcType()))
                            .addEntry(TYPE_NAME, functionMetadata.getReturnType().toString())
                            .addEntry(PRECISION, String.valueOf(returnJdbcType.getPrecision(null)))
                            .addEntry(LENGTH, String.valueOf(Integer.MAX_VALUE))
                            .addEntry(SCALE, String.valueOf(returnJdbcType.getScale(null)))
                            .addEntry(RADIX, String.valueOf(returnJdbcType.getPrecision(null)))
                            .addEntry(NULLABLE, String.valueOf(typeNullable))
                            .addEntry(REMARKS, StringUtils.EMPTY)
                            .addEntry(CHAR_OCTET_LENGTH, null)
                            .addEntry(ORDINAL_POSITION, "0")
                            .addEntry(IS_NULLABLE, YES_VALUE)
                            .addEntry(SPECIFIC_NAME, functionSignature.getName().asInternal());
                        functionParamsRows.add(row);
                        // Function input parameters.
                        final List<CqlIdentifier> paramNames = functionMetadata.getParameterNames();
                        for (int i = 0; i < paramNames.size(); i++) {
                            if (WILDCARD_CHAR.equals(columnNamePattern) || columnNamePattern == null
                                || columnNamePattern.equals(paramNames.get(i).asInternal())) {
                                final AbstractJdbcType<?> paramJdbcType = getTypeForComparator(
                                    functionSignature.getParameterTypes().get(i).asCql(false, true));
                                final MetadataRow paramRow = new MetadataRow()
                                    .addEntry(FUNCTION_CATALOG, statement.connection.getCatalog())
                                    .addEntry(FUNCTION_SCHEMA, keyspaceMetadata.getName().asInternal())
                                    .addEntry(FUNCTION_NAME, functionSignature.getName().asInternal())
                                    .addEntry(COLUMN_NAME, paramNames.get(i).asInternal())
                                    .addEntry(COLUMN_TYPE, String.valueOf(functionColumnIn))
                                    .addEntry(DATA_TYPE, String.valueOf(paramJdbcType.getJdbcType()))
                                    .addEntry(TYPE_NAME, functionSignature.getParameterTypes().get(i).toString())
                                    .addEntry(PRECISION, String.valueOf(paramJdbcType.getPrecision(null)))
                                    .addEntry(LENGTH, String.valueOf(Integer.MAX_VALUE))
                                    .addEntry(SCALE, String.valueOf(paramJdbcType.getScale(null)))
                                    .addEntry(RADIX, String.valueOf(paramJdbcType.getPrecision(null)))
                                    .addEntry(NULLABLE, String.valueOf(typeNullable))
                                    .addEntry(REMARKS, StringUtils.EMPTY)
                                    .addEntry(CHAR_OCTET_LENGTH, null)
                                    .addEntry(ORDINAL_POSITION, String.valueOf(i + 1))
                                    .addEntry(IS_NULLABLE, YES_VALUE)
                                    .addEntry(SPECIFIC_NAME, functionSignature.getName().asInternal());
                                functionParamsRows.add(paramRow);
                            }
                        }
                    }
                }
            }
        }

        // Results should all have the same FUNCTION_CAT, so just sort them by FUNCTION_SCHEM then FUNCTION_NAME (since
        // here SPECIFIC_NAME is equal to FUNCTION_NAME), and finally by ORDINAL_POSITION.
        functionParamsRows.sort(Comparator.comparing(row -> ((MetadataRow) row).getString(FUNCTION_SCHEMA))
            .thenComparing(row -> ((MetadataRow) row).getString(FUNCTION_NAME))
            .thenComparing(row -> ((MetadataRow) row).getString(SPECIFIC_NAME))
            .thenComparing(row -> Integer.valueOf(((MetadataRow) row).getString(ORDINAL_POSITION))));
        return CassandraMetadataResultSet.buildFrom(statement, new MetadataResultSet().setRows(functionParamsRows));
    }
}
