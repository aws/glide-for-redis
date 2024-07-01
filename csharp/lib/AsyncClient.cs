// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Buffers;
using System.Runtime.InteropServices;

namespace Glide;

public class AsyncClient : IDisposable
{
    #region public methods
    public AsyncClient(string host, uint port, bool useTLS)
    {
        _successCallbackDelegate = SuccessCallback;
        nint successCallbackPointer = Marshal.GetFunctionPointerForDelegate(_successCallbackDelegate);
        _failureCallbackDelegate = FailureCallback;
        nint failureCallbackPointer = Marshal.GetFunctionPointerForDelegate(_failureCallbackDelegate);
        _clientPointer = CreateClientFfi(host, port, useTLS, successCallbackPointer, failureCallbackPointer);
        if (_clientPointer == IntPtr.Zero)
        {
            throw new Exception("Failed creating a client");
        }
    }

    private async Task<string?> Command(IntPtr[] args, int argsCount, RequestType requestType)
    {
        // We need to pin the array in place, in order to ensure that the GC doesn't move it while the operation is running.
        GCHandle pinnedArray = GCHandle.Alloc(args, GCHandleType.Pinned);
        IntPtr pointer = pinnedArray.AddrOfPinnedObject();
        Message<string> message = _messageContainer.GetMessageForCall(args, argsCount);
        CommandFfi(_clientPointer, (ulong)message.Index, (int)requestType, pointer, (uint)argsCount);
        string? result = await message;
        pinnedArray.Free();
        return result;
    }

    public async Task<string?> SetAsync(string key, string value)
    {
        IntPtr[] args = _arrayPool.Rent(2);
        args[0] = Marshal.StringToHGlobalAnsi(key);
        args[1] = Marshal.StringToHGlobalAnsi(value);
        string? result = await Command(args, 2, RequestType.Set);
        _arrayPool.Return(args);
        return result;
    }

    public async Task<string?> GetAsync(string key)
    {
        IntPtr[] args = _arrayPool.Rent(1);
        args[0] = Marshal.StringToHGlobalAnsi(key);
        string? result = await Command(args, 1, RequestType.Get);
        _arrayPool.Return(args);
        return result;
    }

    public void Dispose()
    {
        if (_clientPointer == IntPtr.Zero)
        {
            return;
        }
        _messageContainer.DisposeWithError(null);
        CloseClientFfi(_clientPointer);
        _clientPointer = IntPtr.Zero;
    }

    #endregion public methods

    #region private methods

    private void SuccessCallback(ulong index, IntPtr str)
    {
        string? result = str == IntPtr.Zero ? null : Marshal.PtrToStringAnsi(str);
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        _ = Task.Run(() =>
        {
            Message<string> message = _messageContainer.GetMessage((int)index);
            message.SetResult(result);
        });
    }

    private void FailureCallback(ulong index) =>
        // Work needs to be offloaded from the calling thread, because otherwise we might starve the client's thread pool.
        Task.Run(() =>
        {
            Message<string> message = _messageContainer.GetMessage((int)index);
            message.SetException(new Exception("Operation failed"));
        });

    ~AsyncClient() => Dispose();
    #endregion private methods

    #region private fields

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly FailureAction _failureCallbackDelegate;

    /// Held as a measure to prevent the delegate being garbage collected. These are delegated once
    /// and held in order to prevent the cost of marshalling on each function call.
    private readonly StringAction _successCallbackDelegate;

    /// Raw pointer to the underlying native client.
    private IntPtr _clientPointer;
    private readonly MessageContainer<string> _messageContainer = new();
    private readonly ArrayPool<IntPtr> _arrayPool = ArrayPool<IntPtr>.Shared;

    #endregion private fields

    #region FFI function declarations

    private delegate void StringAction(ulong index, IntPtr str);
    private delegate void FailureAction(ulong index);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "command")]
    private static extern void CommandFfi(IntPtr client, ulong index, int requestType, IntPtr args, uint argCount);

    private delegate void IntAction(IntPtr arg);
    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "create_client")]
    private static extern IntPtr CreateClientFfi(string host, uint port, bool useTLS, IntPtr successCallback, IntPtr failureCallback);

    [DllImport("libglide_rs", CallingConvention = CallingConvention.Cdecl, EntryPoint = "close_client")]
    private static extern void CloseClientFfi(IntPtr client);

    #endregion

    #region RequestType

    // TODO: generate this with a bindings generator
    private enum RequestType
    {
        InvalidRequest = 0,
        CustomCommand = 1,
        Get = 2,
        Set = 3,
        Ping = 4,
        Info = 5,
        Del = 6,
        Select = 7,
        ConfigGet = 8,
        ConfigSet = 9,
        ConfigResetStat = 10,
        ConfigRewrite = 11,
        ClientGetName = 12,
        ClientGetRedir = 13,
        ClientId = 14,
        ClientInfo = 15,
        ClientKill = 16,
        ClientList = 17,
        ClientNoEvict = 18,
        ClientNoTouch = 19,
        ClientPause = 20,
        ClientReply = 21,
        ClientSetInfo = 22,
        ClientSetName = 23,
        ClientUnblock = 24,
        ClientUnpause = 25,
        Expire = 26,
        HSet = 27,
        HGet = 28,
        HDel = 29,
        HExists = 30,
        MGet = 31,
        MSet = 32,
        Incr = 33,
        IncrBy = 34,
        Decr = 35,
        IncrByFloat = 36,
        DecrBy = 37,
        HGetAll = 38,
        HMSet = 39,
        HMGet = 40,
        HIncrBy = 41,
        HIncrByFloat = 42,
        LPush = 43,
        LPop = 44,
        RPush = 45,
        RPop = 46,
        LLen = 47,
        LRem = 48,
        LRange = 49,
        LTrim = 50,
        SAdd = 51,
        SRem = 52,
        SMembers = 53,
        SCard = 54,
        PExpireAt = 55,
        PExpire = 56,
        ExpireAt = 57,
        Exists = 58,
        Unlink = 59,
        TTL = 60,
        ZAdd = 61,
        ZRem = 62,
        ZRange = 63,
        ZCard = 64,
        ZCount = 65,
        ZIncrBy = 66,
        ZScore = 67,
        Type = 68,
        HLen = 69,
        Echo = 70,
        ZPopMin = 71,
        Strlen = 72,
        LIndex = 73,
        ZPopMax = 74,
        XRead = 75,
        XAdd = 76,
        XReadGroup = 77,
        XAck = 78,
        XTrim = 79,
        XGroupCreate = 80,
        XGroupDestroy = 81,
        HSetNX = 82,
        SIsMember = 83,
        HVals = 84,
        PTTL = 85,
        ZRemRangeByRank = 86,
        Persist = 87,
        ZRemRangeByScore = 88,
        Time = 89,
        ZRank = 90,
        Rename = 91,
        DBSize = 92,
        BRPop = 93,
        HKeys = 94,
        PfAdd = 96,
        PfCount = 97,
        PfMerge = 98,
        BLPop = 100,
        RPushX = 102,
        LPushX = 103,
    }

    #endregion
}
