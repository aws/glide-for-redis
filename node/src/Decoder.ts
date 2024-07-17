import { GlideString } from "./BaseClient";
export interface Decoder<T> {
    decode(input: GlideString): T;
}

class BytesDecoder implements Decoder<any> {
    decode(input: GlideString): any {}
}

class StringDecoder implements Decoder<any> {
    decode(input: GlideString): any {
        if (Buffer.isBuffer(input)) {
            return input.toString("utf-8");
        } else if (Array.isArray(input)) {
            return input.map(this.decode);
        } else if (input instanceof Set) {
            return new Set(Array.from(input).map(this.decode));
        } else if (input instanceof Map) {
            const decodedMap = new Map();
            for (const [key, value] of input) {
                decodedMap.set(this.decode(key), this.decode(value));
            }
            return decodedMap;
        } else if (typeof input === "object" && input !== null) {
            const decodedObject: Record<string, any> = {};
            for (const [key, value] of Object.entries(input)) {
                decodedObject[this.decode(key) as string] = this.decode(value);
            }
            return decodedObject;
        } else {
            return input; // Return as is if not a buffer, array, set, map, or object
        }
    }
}
