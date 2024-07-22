export interface Decoder {
    // TODO : change the input to retunType
    decode<T>(input: T): any; 
}

export class BytesDecoder implements Decoder {
    decode<T>(input: T): any { return input; }
}

export class StringDecoder implements Decoder {
    decode<T>(input: T): any { return input; }
}
