export interface Decoder {
    decode<T>(input: T): any;
}

export class BytesDecoder implements Decoder {
    decode<T>(input: T): any { return input; }
}

export class StringDecoder implements Decoder {
    decode<T>(input: T): any {
        if (input == null) { return input; }
        else if (input instanceof Uint8Array) {
            return input.toString();
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
        // Object Handling: For each key-value pair, decode the key if it is a Uint8Array and decode the value. 
        } else if (typeof input === "object") {
            var decodedObject: { [key: string]: any }  = new Object();
            for (const [key, value] of Object.entries(input)) {
                const anyKey: any = key;
                // if(anyKey instanceof Uint8Array) {
                //     decodedObject[anyKey.toString()] = this.decode(value);
                // } else {
                //     decodedObject[anyKey] = this.decode(value);
                // }
                const decodedKey = anyKey instanceof Uint8Array ? anyKey.toString() : this.decode(anyKey);
                decodedObject[decodedKey] = this.decode(value);
            }
            return decodedObject;
        } else {
            return input; // Return as is if not a uint8Arry, array, set, map, or object
        }
    }
}
