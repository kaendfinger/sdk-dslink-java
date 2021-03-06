package org.dsa.iot.dslink.serializer;

import org.dsa.iot.dslink.node.Node;
import org.dsa.iot.dslink.node.NodeManager;
import org.dsa.iot.dslink.node.Writable;
import org.dsa.iot.dslink.node.value.ValueType;
import org.dsa.iot.dslink.node.value.ValueUtils;
import org.dsa.iot.dslink.util.json.JsonObject;

import java.util.Map;

/**
 * Deserializes a JSON file into a node manager
 *
 * @author Samuel Grenier
 */
public class Deserializer {

    private final NodeManager manager;

    public Deserializer(NodeManager manager) {
        this.manager = manager;
    }

    /**
     * Deserializes the object into the manager.
     *
     * @param object Object to deserialize.
     */
    @SuppressWarnings("unchecked")
    public void deserialize(JsonObject object) {
        for (Map.Entry<String, Object> entry : object) {
            String name = entry.getKey();
            Node node = manager.getNode(name, true).getNode();
            Object value = entry.getValue();
            JsonObject data = (JsonObject) value;
            deserializeNode(node, data);
        }
    }

    @SuppressWarnings("unchecked")
    private void deserializeNode(Node node, JsonObject map) {
        final String type = map.get("$type");
        if (type != null) {
            ValueType t = ValueType.toValueType(type);
            node.setValueType(t);
        }
        for (Map.Entry<String, Object> entry : map) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null || "$type".equals(name)) {
                continue;
            }
            if ("$is".equals(name)) {
                node.setProfile((String) value);
            } else if ("$interface".equals(name)) {
                node.setInterfaces((String) value);
            } else if ("$name".equals(name)) {
                node.setDisplayName((String) value);
            } else if ("$writable".equals(name)) {
                node.setWritable(Writable.toEnum((String) value));
            } else if ("$hidden".equals(name)) {
                node.setHidden((Boolean) value);
            } else if ("$$password".equals(name)) {
                node.setPassword(((String) value).toCharArray());
            } else if ("?value".equals(name)) {
                node.setValue(ValueUtils.toValue(value));
            } else if (name.startsWith("$$")) {
                node.setRoConfig(name.substring(2), ValueUtils.toValue(value));
            } else if (name.startsWith("$")) {
                node.setConfig(name.substring(1), ValueUtils.toValue(value));
            } else if (name.startsWith("@")) {
                node.setAttribute(name.substring(1), ValueUtils.toValue(value));
            } else {
                Node child = node.createChild(name).build();
                JsonObject children = (JsonObject) value;
                deserializeNode(child, children);
            }
        }
    }
}
