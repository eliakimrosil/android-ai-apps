package com.screenstream.rtsp.rtmp;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AmfUtil {

    public static final byte TYPE_NUMBER = 0x00;
    public static final byte TYPE_BOOLEAN = 0x01;
    public static final byte TYPE_STRING = 0x02;
    public static final byte TYPE_OBJECT = 0x03;
    public static final byte TYPE_NULL = 0x05;
    public static final byte TYPE_UNDEFINED = 0x06;
    public static final byte TYPE_REFERENCE = 0x07;
    public static final byte TYPE_ECMA_ARRAY = 0x08;
    public static final byte TYPE_OBJECT_END = 0x09;
    public static final byte TYPE_STRICT_ARRAY = 0x0A;
    public static final byte TYPE_DATE = 0x0B;
    public static final byte TYPE_LONG_STRING = 0x0C;

    public static void writeString(DataOutputStream out, String val) throws IOException {
        out.writeByte(TYPE_STRING);
        byte[] bytes = val.getBytes("UTF-8");
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    public static void writeNumber(DataOutputStream out, double val) throws IOException {
        out.writeByte(TYPE_NUMBER);
        out.writeDouble(val);
    }

    public static void writeBoolean(DataOutputStream out, boolean val) throws IOException {
        out.writeByte(TYPE_BOOLEAN);
        out.writeByte(val ? 1 : 0);
    }

    public static void writeNull(DataOutputStream out) throws IOException {
        out.writeByte(TYPE_NULL);
    }

    public static void writeObject(DataOutputStream out, Map<String, Object> map) throws IOException {
        out.writeByte(TYPE_OBJECT);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                writeProperty(out, entry.getKey(), entry.getValue());
            }
        }
        out.writeShort(0);
        out.writeByte(TYPE_OBJECT_END);
    }

    public static void writeEcmaArray(DataOutputStream out, Map<String, Object> map) throws IOException {
        out.writeByte(TYPE_ECMA_ARRAY);
        int size = (map != null) ? map.size() : 0;
        out.writeInt(size);
        if (map != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                writeProperty(out, entry.getKey(), entry.getValue());
            }
        }
        out.writeShort(0);
        out.writeByte(TYPE_OBJECT_END);
    }

    private static void writeProperty(DataOutputStream out, String key, Object val) throws IOException {
        byte[] keyBytes = key.getBytes("UTF-8");
        out.writeShort(keyBytes.length);
        out.write(keyBytes);

        if (val instanceof String) {
            writeString(out, (String) val);
        } else if (val instanceof Number) {
            writeNumber(out, ((Number) val).doubleValue());
        } else if (val instanceof Boolean) {
            writeBoolean(out, (Boolean) val);
        } else if (val instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> subMap = (Map<String, Object>) val;
            writeObject(out, subMap);
        } else if (val == null) {
            writeNull(out);
        }
    }

    public static Object readValue(DataInputStream in) throws IOException {
        int type = in.readUnsignedByte();
        switch (type) {
            case TYPE_NUMBER:
                return in.readDouble();
            case TYPE_BOOLEAN:
                return in.readUnsignedByte() != 0;
            case TYPE_STRING: {
                int len = in.readUnsignedShort();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return new String(bytes, "UTF-8");
            }
            case TYPE_OBJECT: {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int kLen = in.readUnsignedShort();
                    if (kLen == 0) {
                        in.readUnsignedByte(); // read TYPE_OBJECT_END (0x09)
                        break;
                    }
                    byte[] kBytes = new byte[kLen];
                    in.readFully(kBytes);
                    String key = new String(kBytes, "UTF-8");
                    Object val = readValue(in);
                    map.put(key, val);
                }
                return map;
            }
            case TYPE_NULL:
            case TYPE_UNDEFINED:
                return null;
            case TYPE_ECMA_ARRAY: {
                in.readInt(); // count
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int kLen = in.readUnsignedShort();
                    if (kLen == 0) {
                        in.readUnsignedByte(); // read TYPE_OBJECT_END (0x09)
                        break;
                    }
                    byte[] kBytes = new byte[kLen];
                    in.readFully(kBytes);
                    String key = new String(kBytes, "UTF-8");
                    Object val = readValue(in);
                    map.put(key, val);
                }
                return map;
            }
            case TYPE_STRICT_ARRAY: {
                int count = in.readInt();
                List<Object> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(readValue(in));
                }
                return list;
            }
            case TYPE_DATE: {
                in.readDouble(); // date timestamp
                in.readShort();  // timezone
                return null;
            }
            case TYPE_LONG_STRING: {
                int len = in.readInt();
                byte[] bytes = new byte[len];
                in.readFully(bytes);
                return new String(bytes, "UTF-8");
            }
            default:
                return null;
        }
    }
}
