package org.apache.entityobjecthistory;

import java.beans.Transient;
import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public class EntityObjectHistoryContainer implements Serializable {

    private Map<String, TreeMap<Long, Object>> fields;

    private Map<String, Map<String,TreeMap<Long, EntityObjectHistoryContainer>>> listChildren;

    public void setFields(Map<String, TreeMap<Long, Object>> fields) {
        this.fields = fields;
    }

    public void setListChildren(Map<String, Map<String, TreeMap<Long, EntityObjectHistoryContainer>>> listChildren) {
        this.listChildren = listChildren;
    }

    public EntityObjectHistoryContainer() {
        fields = new HashMap<>();
        listChildren = new HashMap<>();
    }

    void addField(String key, Date date, Object value) {
        TreeMap<Long, Object> datesMap;
        if (!fields.containsKey(key)) {
            datesMap = new TreeMap<>();
        } else {
            datesMap = fields.get(key);
        }

        datesMap.put(date.getTime(), value);

        fields.put(key, datesMap);
    }

    void addToListChild(String key, String id, Date date, EntityObjectHistoryContainer child) {
        if (Objects.isNull(listChildren.get(key))) {
            listChildren.put(key, new HashMap<>());
        }

        Map<String, TreeMap<Long, EntityObjectHistoryContainer>> lastChild = listChildren.get(key);

        if (Objects.isNull(lastChild.get(id))) {
            TreeMap<Long, EntityObjectHistoryContainer> children = new TreeMap<>();
            children.put(date.getTime(), child);
            listChildren.get(key).put(id, children);
            return;
        }

        if (Objects.isNull(child)) {
            if (Objects.nonNull(lastChild.get(id).lastEntry().getValue())) {
                lastChild.get(id).put(date.getTime(), null);
            }
        } else {
            if (lastChild.get(id).lastEntry() != null && lastChild.get(id).lastEntry().getValue() == null) {
                lastChild.get(id).put(date.getTime(), child);
            }
        }
    }

    public Map<String, TreeMap<Long, Object>> getFields() {
        return fields;
    }

    public Map<String, Map<String, TreeMap<Long, EntityObjectHistoryContainer>>> getListChildren() {
        return listChildren;
    }

    @Transient
    Set<String> getChildrenIds(String listName) {
        if(Objects.isNull(listChildren.get(listName))) {
            return new HashSet<>();
        }
        return new HashSet<>(this.listChildren.get(listName).keySet());
    }

    private Object getLastValue(Date date, String field) {
        TreeMap<Long, Object> map = this.getFields().get(field);
        return Objects.isNull(map) ? null :
                Objects.isNull(map.floorEntry(date.getTime())) ? null : map.floorEntry(date.getTime()).getValue();
    }

    Object getFieldValue(String field, Date date) {
        if (fields.containsKey(field) && Objects.nonNull(fields.get(field).floorEntry(date.getTime()))) {
            return fields.get(field).floorEntry(date.getTime()).getValue();
        }

        return null;
    }

    boolean hasOldValue(String field, Date date, Object value) {
        Object lastValue = getLastValue(date, field);
        if (Objects.isNull(lastValue)) return false;

        if(value instanceof Date) {
            return String.valueOf(lastValue).equals(String.valueOf(((Date) value).getTime()));
        }

        return String.valueOf(lastValue).equals(String.valueOf(value));
    }

    boolean isFieldChanged(EntityObjectHistoryContainer container, String field, long date) {
        for (String s : container.fields.keySet()) {
            if(s.equals(field)) {
                for (Long aLong : container.fields.get(s).keySet()) {
                    if(aLong.equals(date)) {
                        return true;
                    }
                }
            }
        }

        for (String s : container.listChildren.keySet()) {
            for (String s1 : container.listChildren.get(s).keySet()) {
                for (Long aLong : container.listChildren.get(s).get(s1).keySet()) {
                    if(isFieldChanged(container.listChildren.get(s).get(s1).get(aLong), field, date)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}