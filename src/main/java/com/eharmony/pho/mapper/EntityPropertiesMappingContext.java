package com.eharmony.pho.mapper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.code.morphia.annotations.Transient;
import org.apache.commons.lang.StringUtils;

import com.google.code.morphia.annotations.Embedded;
import com.google.code.morphia.annotations.Property;

/**
 * Provides the mappings between entity properties and datastore mapping columns.
 * 
 * Entity mappings will be resolved for configured classes on instantiation of this class.
 * 
 * @author vvangapandu
 *
 */
public class EntityPropertiesMappingContext {

    private Map<String, Map<String, EntityPropertyBinding>> storeFieldToEntityPropertyBindingMap = new HashMap<String, Map<String, EntityPropertyBinding>>();
    private Map<String, Map<String, EntityPropertyBinding>> entityPropertyToStoreFieldBindingMap = new HashMap<String, Map<String, EntityPropertyBinding>>();

    private static final String PROPERTY_SEPARATOR = ".";
    private static final String STORE_FIELD_LINE = "_";

    public EntityPropertiesMappingContext(List<String> classNames) throws ClassNotFoundException {
        if (classNames != null) {
            for (String clzName : classNames) {
                Class<? extends Object> clz = getClass().getClassLoader().loadClass(clzName);
                populateMappings(clz);
            }

        }
    }

    public void populateMappings(Class<?> clz) {

        String className = clz.getSimpleName();
        Set<EntityPropertyBinding> entityPropertiesSet = buildEntityPropertiesSet(clz, null);
        Map<String, EntityPropertyBinding> entityMappingPropertiesMap = populateEntityMappingPropertiesMap(
                entityPropertiesSet, clz);
        storeFieldToEntityPropertyBindingMap.put(className, entityMappingPropertiesMap);

        Map<String, EntityPropertyBinding> entityFieldPropertiesMap = populateEntityFieldPropertiesMap(
                entityPropertiesSet, clz);
        entityPropertyToStoreFieldBindingMap.put(className, entityFieldPropertiesMap);

    }

    private Map<String, EntityPropertyBinding> populateEntityMappingPropertiesMap(
            Set<EntityPropertyBinding> entityPropertiesSetInternal, Class<?> clz) {
        Map<String, EntityPropertyBinding> propertiesMap = new HashMap<String, EntityPropertyBinding>();
        for (EntityPropertyBinding entityProperty : entityPropertiesSetInternal) {
            propertiesMap.put(entityProperty.getStoreFieldName().toUpperCase(), entityProperty);
        }
        return propertiesMap;
    }

    private Map<String, EntityPropertyBinding> populateEntityFieldPropertiesMap(
            Set<EntityPropertyBinding> entityPropertiesSetInternal, Class<?> clz) {
        Map<String, EntityPropertyBinding> propertiesMap = new HashMap<String, EntityPropertyBinding>();
        for (EntityPropertyBinding entityProperty : entityPropertiesSetInternal) {
            propertiesMap.put(entityProperty.getName(), entityProperty);
            // should the property resolved by complete path?
            // propertiesMap.put(entityProperty.getMappingNameFullPath(), entityProperty);
        }
        return propertiesMap;
    }

    public EntityPropertyBinding resolveEntityPropertyBindingByStoreMappingName(Class<?> clz, String mappingName) {
        Map<String, EntityPropertyBinding> entityProperties = storeFieldToEntityPropertyBindingMap.get(clz
                .getSimpleName());

        if (entityProperties == null || entityProperties.size() == 0) {
            return null;
        }
        return entityProperties.get(mappingName.toUpperCase());
    }
    
    public EntityPropertyBinding resolveEntityPropertyBindingByEntityFieldName(Class<?> clz, String fieldName) {
        Map<String, EntityPropertyBinding> entityProperties = storeFieldToEntityPropertyBindingMap.get(clz
                .getSimpleName());
        if (entityProperties == null || entityProperties.size() == 0) {
            return null;
        }
        EntityPropertyBinding entityProperty = entityProperties.get(fieldName);
        if (entityProperty != null && StringUtils.isNotBlank(entityProperty.getStoreFieldName())) {
            return entityProperty;
        }
        return null;
    }

    public String resolveEntityMappingPropertyName(Class<?> clz, String fieldName) {
        Map<String, EntityPropertyBinding> entityProperties = entityPropertyToStoreFieldBindingMap.get(clz
                .getSimpleName());
        if (entityProperties == null || entityProperties.size() == 0) {
            return fieldName;
        }
        EntityPropertyBinding entityProperty = entityProperties.get(fieldName);
        if (entityProperty != null && StringUtils.isNotBlank(entityProperty.getStoreFieldName())) {
            return entityProperty.getStoreFieldName();
        }
        return fieldName;
    }

    public List<String> resolveEntityMappingPropertyNames(Class<?> clz, List<String> fieldPropertyNames) {
        Map<String, EntityPropertyBinding> entityProperties = entityPropertyToStoreFieldBindingMap.get(clz
                .getSimpleName());

        if (entityProperties == null || entityProperties.size() == 0) {
            throw new IllegalArgumentException("Invalid Entity Class:" + clz.getSimpleName());
        }
        List<String> mappingProperties = new LinkedList<String>();
        for (String fieldPropertyName : fieldPropertyNames) {
            EntityPropertyBinding entityProperty = entityProperties.get(fieldPropertyName);
            String mappingName = fieldPropertyName;
            if (entityProperty != null && StringUtils.isNotBlank(entityProperty.getStoreFieldName())) {
                mappingName = entityProperty.getStoreFieldName();
            }
            mappingProperties.add(mappingName);
        }
        return mappingProperties;
    }

    private Set<EntityPropertyBinding> buildEntityPropertiesSet(Class<? extends Object> clz, String parentProperty) {

        Set<EntityPropertyBinding> entityPropertiesSet = new HashSet<EntityPropertyBinding>();
        do {
            Field[] fields = clz.getDeclaredFields();
            if (clz.getSuperclass() == Object.class && (fields == null || fields.length == 0)) {
                return entityPropertiesSet;
            }
            for (Field field : fields) {
                if (field.isAnnotationPresent(Embedded.class)) {
                    StringBuilder nameBuilder = new StringBuilder();
                    if (StringUtils.isNotEmpty(parentProperty)) {
                        nameBuilder.append(parentProperty).append(PROPERTY_SEPARATOR);
                    }
                    nameBuilder.append(field.getName());
                    entityPropertiesSet.addAll(buildEntityPropertiesSet(field.getType(), nameBuilder.toString()));
                } else {
                    EntityPropertyBinding entityProperty = buildEntityProperty(field, parentProperty);
                    if (entityProperty != null) {
                        entityPropertiesSet.add(entityProperty);
                    }
                }
            }
            // 循环父类属性
            clz = clz.getSuperclass();
        } while (clz != Object.class);

        return entityPropertiesSet;
    }

    private EntityPropertyBinding buildEntityProperty(Field simpleField, String parentProperty) {

        EntityPropertyBinding entityProperty = new EntityPropertyBinding();
        Property propertyAnnotation = simpleField.getAnnotation(Property.class);
        if (propertyAnnotation == null) {
            // Mapping store column doesn't exist for field, consider it as non store property.
            // should we consider property name as store field name in absence of annotation????
            // entityProperty.setStoreFieldName(simpleField.getName());

            Transient transientAnnotation = simpleField.getAnnotation(Transient.class);
            if (transientAnnotation != null || Modifier.isStatic(simpleField.getModifiers())) {
                // ignore static field
                return null;
            } else {
                /**
                 * 没有Property和Transient注解时，默认驼峰转下划线方式
                 */
                String[] camelFieldArray = StringUtils.splitByCharacterTypeCamelCase(simpleField.getName());
                StringBuilder storeFieldNameSb = new StringBuilder();
                for (String namePart: camelFieldArray) {
                    storeFieldNameSb.append(namePart.toUpperCase()).append(STORE_FIELD_LINE);
                }
                if (storeFieldNameSb.length() > 0) {
                    storeFieldNameSb.deleteCharAt(storeFieldNameSb.length() - 1);
                }
                entityProperty.setStoreFieldName(storeFieldNameSb.toString());
            }
        } else {
            entityProperty.setStoreFieldName(propertyAnnotation.value());
        }

        entityProperty.setName(simpleField.getName());
        entityProperty.setType(simpleField.getType());
        StringBuilder nameBuilder = new StringBuilder();
        if (StringUtils.isNotEmpty(parentProperty)) {
            nameBuilder.append(parentProperty).append(PROPERTY_SEPARATOR);
        }
        nameBuilder.append(simpleField.getName());
        entityProperty.setNameFullPath(nameBuilder.toString());
        return entityProperty;
    }

    public String resolve(String fieldName, Class<?> entityClass) {
        Map<String, EntityPropertyBinding> propertiesMap = entityPropertyToStoreFieldBindingMap.get(entityClass
                .getSimpleName());

        if (propertiesMap == null) {
            throw new IllegalArgumentException("Invalid Entity class:" + entityClass.getSimpleName());
        }

        EntityPropertyBinding entityProperty = propertiesMap.get(fieldName);
        if (entityProperty != null && StringUtils.isNotBlank(entityProperty.getStoreFieldName())) {
            return entityProperty.getStoreFieldName();
        }
        return fieldName;
    }
    
    public <T> Map<String, EntityPropertyBinding> getStoreFieldNamePropertyBindingMap(Class<T> clz) {
        return storeFieldToEntityPropertyBindingMap.get(clz.getSimpleName());
    }

    public <T> Map<String, EntityPropertyBinding> getEntityPropertyNamePropertyBindingMap(Class<T> clz) {
        return entityPropertyToStoreFieldBindingMap.get(clz.getSimpleName());
    }

}
