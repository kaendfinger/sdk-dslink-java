package org.dsa.iot.dslink.responder;

import lombok.NonNull;
import lombok.val;
import net.engio.mbassy.bus.MBassador;
import org.dsa.iot.core.event.Event;
import org.dsa.iot.dslink.connection.Client;
import org.dsa.iot.dslink.events.RequestEvent;
import org.dsa.iot.dslink.responder.methods.*;
import org.dsa.iot.dslink.util.Linkable;
import org.dsa.iot.dslink.util.StreamState;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.dsa.iot.dslink.node.NodeManager.NodeStringTuple;

/**
 * @author Samuel Grenier
 */
public class Responder extends Linkable {

    public Responder(MBassador<Event> bus) {
        super(bus);
    }

    /**
     * @param requests The requests the other endpoint wants
     */
    @Override
    @SuppressWarnings("unchecked")
    public synchronized void parse(final Client client, final JsonArray requests) {
        val it = requests.iterator();
        for (JsonObject obj; it.hasNext();) {
            obj = (JsonObject) it.next();
            final JsonObject o = obj;
            
            final Number rid = o.getNumber("rid");
            final String method = o.getString("method");
            if (rid == null) {
                continue;
            } else if (method == null) {
                val resp = new JsonObject();
                handleInvocationError(resp, "Missing method field", null);
                
                val responses = new JsonArray();
                responses.addElement(resp);
                val top = new JsonObject();
                top.putElement("responses", responses);
                client.write(top);
                continue;
            }
            
            val event = new RequestEvent(client, o,
                                            rid.intValue(),
                                            method);
            getBus().publish(event);
            handleRequest(client, rid.intValue(), method, o);
        }
    }

    public void closeStream(Client client, int rid) {
        if (client.getResponseTracker().isTracking(rid)) {
            client.getResponseTracker().untrack(rid);

            val array = new JsonArray();
            {
                val obj = new JsonObject();
                obj.putNumber("rid", rid);
                obj.putString("stream", StreamState.CLOSED.jsonName);
                array.add(obj);
            }

            val resp = new JsonObject();
            resp.putArray("responses", array);
            client.write(resp);
        }
    }
    
    protected void handleRequest(Client client, int rid,
                                 String sMethod, JsonObject obj) {
        val resp = new JsonObject();
        boolean error = false;
        Method method = null;
        try {
            val path = obj.getString("path");
            resp.putNumber("rid", rid);

            NodeStringTuple node = null;
            if (path != null) {
                node = getManager().getNode(path);
            }

            method = getMethod(obj, client, sMethod, rid, node);
            val updates = method.invoke();
            val state = method.getState();

            if (state == null) {
                throw new IllegalStateException("state");
            }
            if (state != StreamState.INITIALIZED) {
                // This is a first response, default value is initialized if omitted
                resp.putString("stream", state.jsonName);
            }
            if (state == StreamState.OPEN
                    || state == StreamState.INITIALIZED) {
                client.getResponseTracker().track(rid);
            }
            if (updates != null && updates.size() > 0) {
                resp.putElement("updates", updates);
            }
        } catch (Exception e) {
            error = true;
            handleInvocationError(resp, e);
        } finally {
            val responses = new JsonArray();
            responses.addElement(resp);
            val top = new JsonObject();
            top.putElement("responses", responses);
            client.write(top);
            if (method != null && !error) {
                method.postSent();
            }
        }
    }

    protected Method getMethod(@NonNull JsonObject obj,
                                @NonNull Client client,
                                @NonNull String name,
                                int rid,
                                NodeStringTuple tuple) {
        switch (name) {
            case "list":
                return new ListMethod(this, client, tuple.getNode(), rid, obj);
            case "set":
                return new SetMethod(tuple.getNode(), tuple.getString(), obj);
            case "remove":
                return new RemoveMethod(tuple.getNode(), tuple.getString(), obj);
            case "invoke":
                return new InvokeMethod(tuple.getNode(), obj);
            case "subscribe":
                return new SubscribeMethod(getManager(), obj);
            case "unsubscribe":
                return new UnsubscribeMethod(getManager(), obj);
            case "close":
                return new CloseMethod(getBus(),
                                        client,
                                        client.getResponseTracker(),
                                        this, rid, obj);
            default:
                throw new RuntimeException("Unknown method");
        }
    }
    
    protected void handleInvocationError(JsonObject resp,
                                         @NonNull Exception e) {
        val writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        handleInvocationError(resp, e.getMessage(), writer.toString());
    }

    protected void handleInvocationError(@NonNull JsonObject resp,
                                         @NonNull String msg,
                                         String detail) {
        resp.putString("stream", StreamState.CLOSED.jsonName);
        val error = new JsonObject();
        error.putString("msg", msg);
        if (detail != null) {
            error.putString("detail", detail);
        }
        resp.putElement("error", error);
    }
}
