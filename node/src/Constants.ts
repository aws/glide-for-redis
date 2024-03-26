import { RedisClient, RedisClusterClient } from "../";

export type ReturnTypeMap = { [key: string]: ReturnType };
export type ReturnTypeAttribute = {
    value: ReturnType;
    attributes: ReturnTypeMap;
};
export type ReturnType =
    | "OK"
    | string
    | number
    | null
    | boolean
    | bigint
    | Set<ReturnType>
    | ReturnTypeMap
    | ReturnTypeAttribute
    | ReturnType[];

export type TRedisClient = RedisClient | RedisClusterClient;

/**
 * If the command's routing is to one node we will get T as a response type,
 * otherwise, we will get a dictionary of address: nodeResponse, address is of type string and nodeResponse is of type T.
 */
export type ClusterResponse<T> = T | Record<string, T>;
