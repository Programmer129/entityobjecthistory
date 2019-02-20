package org.apache.entityobjecthistory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class EntityObjectHistoryContainerUtil {

    private static final Logger logger = LoggerFactory.getLogger(EntityObjectHistoryContainerUtil.class);

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Set<Class> allowedClasses;

    private static Set<String> excludedFields;

    static {
        allowedClasses = new HashSet<>(Arrays.asList(
                Integer.class,
                Double.class,
                Long.class,
                Float.class,
                Byte.class,
                Boolean.class,
                Short.class,
                Date.class,
                String.class,
                List.class,
                Enum.class,
                Set.class,
                Character.class,
                ArrayList.class,
                Collection.class));

        //TODO add fields that should to be excluded
        excludedFields = new HashSet<>(Arrays.asList());

        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, Boolean.FALSE);
    }

    private EntityObjectHistoryContainerUtil() {
    }

    public static void delete(String filePath) {
        File superReport = new File(filePath);
        if (superReport.exists() && !superReport.delete()) {
            logger.error("Unable to delete super report: ".concat(filePath));
        }
    }

    public static Object loadHistoryFile(Date date, String filePath, Class clasS) throws IllegalAccessException, InstantiationException {
        File file = new File(filePath);
        EntityObjectHistoryContainer container;
        Object result = clasS.newInstance();

        try {
            container = deserializeJson(file);
            loadFields(result, container, date);
        } catch (IOException | NoSuchMethodException | IllegalAccessException | InvocationTargetException | NoSuchFieldException | InstantiationException | ClassNotFoundException e) {
            logger.error(e.getMessage());
        }

        return result;
    }

    public static Object loadFromContainer(Date date, EntityObjectHistoryContainer container) {
        Object object = null;

        if (container != null) {
            object = new Object();
            try {
                loadFields(object, container, date);
            } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException | NoSuchFieldException | InstantiationException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        return object;
    }

    public static EntityObjectHistoryContainer loadContainerFromFile(String filePath) throws IOException {
        EntityObjectHistoryContainer container = null;
        File file = new File(filePath);

        if (file.exists()) {
            container = deserializeJson(file);
        }

        return container;
    }

    public static EntityObjectHistoryContainer updateContainer(Object report, EntityObjectHistoryContainer container)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        handleFields(report, container, "", null, 1);

        return container;
    }

    public static void saveContainerToFile(EntityObjectHistoryContainer container, String filePath) throws IOException {
        File file = new File(filePath);

        try (FileWriter writer = new FileWriter(file)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, container);
        }
    }

    public static void updateHistoryFile(long id, Object report, String filePath) {
        File file = new File(filePath);

        doUpdate(id, report, file, null);
    }

    public static void updateHistoryFile(long id, Object report, String filePath, Date date) {
        File file = new File(filePath);

        doUpdate(id, report, file, date);
    }

    public static boolean isSecondaryFieldChanged(String field, Date date, String filePath) throws IOException {
        File file = new File(filePath);

        EntityObjectHistoryContainer container = null;

        if (file.exists()) {
            container = deserializeJson(file);
        }

        return Objects.nonNull(container) && container.isFieldChanged(container, field, date.getTime());
    }

    private static void doUpdate(long id, Object object, File file, Date updatedDate) {
        try {
            initialize(file);
            EntityObjectHistoryContainer container = deserializeJson(file);
            handleFields(object, container, "", updatedDate, id);
            try (FileWriter writer = new FileWriter(file)) {
                mapper.writerWithDefaultPrettyPrinter().writeValue(writer, container);
            }
        } catch (RuntimeException | InvocationTargetException | IOException | NoSuchMethodException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static EntityObjectHistoryContainer deserializeJson(File file) throws IOException {
        TypeReference<EntityObjectHistoryContainer> typeRef = new TypeReference<EntityObjectHistoryContainer>() {
        };

        try (FileReader reader = new FileReader(file)) {
            return mapper.readValue(reader, typeRef);
        }
    }

    private static <T> T loadFields(T object, EntityObjectHistoryContainer container, Date date)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException, InstantiationException, ClassNotFoundException {
        Class c = object.getClass();
        Field[] fields = c.getDeclaredFields();

        for (Field field : fields) {
            boolean isFieldAccessible = field.isAccessible();
            field.setAccessible(Boolean.TRUE);

            Object value = getValue(field, object);

            if (Modifier.isStatic(field.getModifiers()))
                continue;

            if (value instanceof List) {
                List<Object> list = loadList(container, field, date);
                field.set(object, list);
            }else if (value != null && allowType(value.getClass())) {
                Object o = loadCustomObject(container, field, date);
                field.set(object, o);
            } else {
                Object result = container.getFieldValue(field.getName(), date);
                if (Objects.nonNull(result)) {
                    field.set(object, fieldType(field, result));
                }
            }
            field.setAccessible(isFieldAccessible);
        }

        return object;
    }

    private static <T> T loadCustomObject(EntityObjectHistoryContainer container, Field field, Date date) throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, NoSuchFieldException {
        Type argument = field.getGenericType();
        T result = null;
        Class<?> aClass = Class.forName(argument.getTypeName());
        Constructor<?> constructor = aClass.getConstructor();
        T object = (T) constructor.newInstance();
        Map.Entry<Long, EntityObjectHistoryContainer> longWhTuReportHistoryContainerEntry
                = container.getListChildren().get(field.getName()).get("1").floorEntry(date.getTime());
        if (Objects.nonNull(longWhTuReportHistoryContainerEntry) && Objects.nonNull(longWhTuReportHistoryContainerEntry.getValue())) {
            result = loadFields(object, longWhTuReportHistoryContainerEntry.getValue(), date);
        }

        return result;
    }

    private static <T> List<T> loadList(EntityObjectHistoryContainer container, Field field, Date date)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException, NoSuchFieldException, InstantiationException, ClassNotFoundException {
        List<T> objects = new ArrayList<>();

        for (String childrenId : container.getChildrenIds(field.getName())) {
            Type argument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            Class<?> aClass = Class.forName(argument.getTypeName());
            Constructor<?> constructor = aClass.getConstructor();
            T object = (T) constructor.newInstance();
            Map.Entry<Long, EntityObjectHistoryContainer> longWhTuReportHistoryContainerEntry
                    = container.getListChildren().get(field.getName()).get(childrenId).floorEntry(date.getTime());
            if (Objects.nonNull(longWhTuReportHistoryContainerEntry) && Objects.nonNull(longWhTuReportHistoryContainerEntry.getValue())) {
                T listItem = loadFields(object, longWhTuReportHistoryContainerEntry.getValue(), date);
                objects.add(listItem);
            }
        }

        return objects;
    }

    private static void initialize(File file) throws IOException {
        if (!file.exists()) {
            if (file.getParentFile() != null && !file.getParentFile().exists()) {
                File parentFile = file.getParentFile();
                if (Objects.nonNull(parentFile) && !parentFile.mkdirs()) {
                    logger.error("Unable to create dirs");
                }
            }
            boolean newFile = file.createNewFile();
            if (!newFile) {
                logger.error("Unable to create file");
                return;
            }
            Files.write(file.toPath(), "{}".getBytes());
        }
    }

    private static <T> void handleFields(T object, EntityObjectHistoryContainer container, String fieldId, Object updatedDate, long privateId)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Class c = object.getClass();
        Field[] fields = c.getDeclaredFields();

        for (Field field : fields) {
            boolean isFiledAccessible = field.isAccessible();
            field.setAccessible(Boolean.TRUE);
            Object value = getValue(field, object);

            if (fieldId.equals("")) {
                fieldId = field.getName();
            } else {
                fieldId = fieldId.concat(".").concat(field.getName());
            }

            if (Objects.isNull(container)
                    || Objects.isNull(value)
                    || Modifier.isStatic(field.getModifiers())
                    || excludedFields.contains(field.getName())
                    || excludedFields.contains(fieldId)) {
                if (fieldId.contains(".")) {
                    fieldId = fieldId.substring(0, fieldId.lastIndexOf("."));
                } else {
                    fieldId = "";
                }
                continue;
            }

            Method[] methods = object.getClass().getMethods();
            Method getUpdateDateMethod = null;
            Method lastUpdated = null;
            for (Method method : methods) {
                if (method.getName().equals("getUpdatedDate")) {
                    getUpdateDateMethod = method;
                }
                if (method.getName().equals("getLastUpdatedTs")) {
                    lastUpdated = method;
                }
            }

            if (updatedDate == null) {
                if (Objects.isNull(getUpdateDateMethod) || Objects.isNull(getUpdateDateMethod.invoke(object))) {
                    if (Objects.nonNull(lastUpdated) && Objects.nonNull(lastUpdated.invoke(object))) {
                        updatedDate = lastUpdated.invoke(object);
                    } else {
                        updatedDate = new Date();
                    }
                } else {
                    updatedDate = getUpdateDateMethod.invoke(object);
                }
            }

            if ((value instanceof List) || (value instanceof Set)) {
                handleCollection(container, value, field.getName(), updatedDate, fieldId, privateId);
            }  else if (allowType(value.getClass())) {
                handleCustomObject(container, field, value, updatedDate, fieldId, privateId);
            } else {
                if (!container.hasOldValue(field.getName(), ((Date) updatedDate), value)) {
                    container.addField(field.getName(), ((Date) updatedDate), value);
                }

            }
            field.setAccessible(isFiledAccessible);
            if (fieldId.contains(".")) {
                fieldId = fieldId.substring(0, fieldId.lastIndexOf("."));
            } else {
                fieldId = "";
            }
        }
    }

    private static void handleCollection(EntityObjectHistoryContainer container, Object value, String listName, Object lastUpdatedTs, String fieldId, long privateId)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        Map<String, Boolean> keysStatus = new HashMap<>();
        container.getChildrenIds(listName).forEach(id -> keysStatus.put(id, Boolean.FALSE));
        Collection collection;
        if (value instanceof Set) {
            collection = ((Set)value);
        } else if (value instanceof List) {
            collection = ((List)value);
        } else {
            return;
        }

        for (Object o : collection) {
            handleListItem(container, o, keysStatus, listName, lastUpdatedTs, fieldId, privateId);
        }

        keysStatus.entrySet().stream()
                .filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey)
                .forEach(key -> container.addToListChild(listName, key, ((Date) lastUpdatedTs), null));
    }

    private static void handleListItem(EntityObjectHistoryContainer container, Object value, Map<String, Boolean> keysStatus, String listName, Object lastUpdatedTs, String fieldId, long privateId)
            throws IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        Method[] methods = value.getClass().getDeclaredMethods();
        Field[] fields = value.getClass().getDeclaredFields();

        if (!value.getClass().isAnnotationPresent(Entity.class)) {
            return;
        }

        Method idGetter = Arrays.stream(methods).filter(method -> method.isAnnotationPresent(Id.class)).findFirst().orElse(null);
        Field idField = Arrays.stream(fields).filter(method -> method.isAnnotationPresent(Id.class)).findFirst().orElse(null);
        Object result = null;
        if (Objects.nonNull(idGetter)) {
            result = idGetter.invoke(value);
        } else if (Objects.nonNull(idField)) {
            idField.setAccessible(true);
            result = idField.get(value);
        }

        if (Objects.isNull(result)) {
            return;
        }

        keysStatus.put(String.valueOf(result), Boolean.TRUE);

        EntityObjectHistoryContainer childContainer;
        Map<String, Map<String, TreeMap<Long, EntityObjectHistoryContainer>>> listMap = container.getListChildren();
        if (!listMap.containsKey(listName)) {
            listMap.put(listName, new HashMap<>());
        }
        Map<String, TreeMap<Long, EntityObjectHistoryContainer>> children = listMap.get(listName);
        if (children.containsKey(String.valueOf(result))
                && Objects.nonNull(container.getListChildren().get(listName))
                && Objects.nonNull(container.getListChildren().get(listName).get(String.valueOf(result)))
                && Objects.nonNull(children.get(result))
                && Objects.nonNull(children.get(result).lastEntry())
                && Objects.nonNull(children.get(result).lastEntry().getValue())) {
            childContainer = container.getListChildren().get(listName).get(String.valueOf(result)).lastEntry().getValue();
        } else {
            childContainer = new EntityObjectHistoryContainer();
        }
        handleFields(value, childContainer, fieldId, lastUpdatedTs, privateId);
        container.addToListChild(listName, String.valueOf(result), ((Date) lastUpdatedTs), childContainer);
    }

    private static void handleCustomObject(EntityObjectHistoryContainer container, Field field, Object value, Object lastUpdatedTs, String fieldId, long privateId)
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        EntityObjectHistoryContainer childContainer;
        if (container.getListChildren().containsKey(field.getName())) {
            if (Objects.isNull(container.getListChildren().get(field.getName()).get("1"))) {
                container.getListChildren().get(field.getName()).put("1", new TreeMap<>());
                childContainer = new EntityObjectHistoryContainer();
            } else {
                childContainer = container.getListChildren().get(field.getName()).get("1").lastEntry().getValue();
            }
        } else {
            childContainer = new EntityObjectHistoryContainer();
        }
        handleFields(value, childContainer, fieldId, lastUpdatedTs, privateId);
        container.addToListChild(field.getName(), "1", ((Date) lastUpdatedTs), childContainer);
    }

    private static boolean allowType(Class type) {
        return !allowedClasses.contains(type);
    }

    private static <T> Object getValue(Field field, T object) {
        try {
            return field.get(object);
        } catch (IllegalAccessException e) {
            logger.error(e.getMessage());
        }

        return null;
    }

    private static Object fieldType(Field field, Object result) {
        if (result instanceof Date) {
            return result;
        }
        switch (field.getGenericType().getTypeName()) {
            case "java.lang.Long":
                return Long.valueOf(String.valueOf(result));
            case "java.lang.Float":
                return Float.valueOf(String.valueOf(result));
            case "java.util.Date":
                return new Date(Long.valueOf(String.valueOf(result)));
            default:
                return result;
        }
    }
}