/* tslint:disable */
/* eslint-disable */

/* auto-generated by NAPI-RS */

export const enum Level {
  Debug = 3,
  Error = 0,
  Info = 2,
  Trace = 4,
  Warn = 1
}
export const MAX_REQUEST_ARGS_LEN: number
export function StartSocketConnection(): Promise<string>
export function log(logLevel: Level, logIdentifier: string, message: string): void
export function InitInternalLogger(level?: Level | undefined | null, fileName?: string | undefined | null): Level
export function valueFromSplitPointer(highBits: number, lowBits: number): null | string | number | any[]
export function stringFromPointer(pointerAsBigint: bigint): string
/**
 * This function is for tests that require a value allocated on the heap.
 * Should NOT be used in production.
 */
export function createLeakedValue(message: string): bigint
export function createLeakedStringVec(message: Array<string>): [number, number]
export class AsyncClient {
  static CreateConnection(connectionAddress: string): AsyncClient
  get(key: string): Promise<string | null>
  set(key: string, value: string): Promise<string | "OK" | null>
}
