/**
 * Copyright GLIDE-for-Redis Project Contributors - SPDX Identifier: Apache-2.0
 */

using System.Diagnostics;
using System.Runtime.CompilerServices;
using System.Runtime.InteropServices;

using Glide;

/// Reusable source of ValueTask. This object can be allocated once and then reused
/// to create multiple asynchronous operations, as long as each call to CreateTask
/// is awaited to completion before the next call begins.
internal class Message<T>(int index, MessageContainer<T> container) : INotifyCompletion
{
    /// This is the index of the message in an external array, that allows the user to
    /// know how to find the message and set its result.
    public int Index { get; } = index;

    /// The pointer to the unmanaged memory that contains the operation's key.
    public IntPtr KeyPtr { get; private set; }

    /// The pointer to the unmanaged memory that contains the operation's key.
    public IntPtr ValuePtr { get; private set; }
    private readonly MessageContainer<T> _container = container;
    private Action? _continuation = () => { };
    private const int COMPLETION_STAGE_STARTED = 0;
    private const int COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION = 1;
    private const int COMPLETION_STAGE_CONTINUATION_EXECUTED = 2;
    private int _completionState;
    private T? _result;
    private Exception? _exception;

    /// Triggers a succesful completion of the task returned from the latest call
    /// to CreateTask.
    public void SetResult(T? result)
    {
        _result = result;
        FinishSet();
    }

    /// Triggers a failure completion of the task returned from the latest call to
    /// CreateTask.
    public void SetException(Exception exc)
    {
        _exception = exc;
        FinishSet();
    }

    private void FinishSet()
    {
        FreePointers();

        CheckRaceAndCallContinuation();
    }

    private void CheckRaceAndCallContinuation()
    {
        if (Interlocked.CompareExchange(ref _completionState, COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION, COMPLETION_STAGE_STARTED) == COMPLETION_STAGE_NEXT_SHOULD_EXECUTE_CONTINUATION)
        {
            Debug.Assert(_continuation != null);
            _completionState = COMPLETION_STAGE_CONTINUATION_EXECUTED;
            try
            {
                _continuation();
            }
            finally
            {
                _container.ReturnFreeMessage(this);
            }
        }
    }

    public Message<T> GetAwaiter() => this;

    /// This returns a task that will complete once SetException / SetResult are called,
    /// and ensures that the internal state of the message is set-up before the task is created,
    /// and cleaned once it is complete.
    public void StartTask(string? key, string? value, object client)
    {
        _continuation = null;
        _completionState = COMPLETION_STAGE_STARTED;
        _result = default;
        _exception = null;
        _client = client;
        KeyPtr = key is null ? IntPtr.Zero : Marshal.StringToHGlobalAnsi(key);
        ValuePtr = value is null ? IntPtr.Zero : Marshal.StringToHGlobalAnsi(value);
    }

    // This function isn't thread-safe. Access to it should be from a single thread, and only once per operation.
    // For the sake of performance, this responsibility is on the caller, and the function doesn't contain any safety measures.
    private void FreePointers()
    {
        if (KeyPtr != IntPtr.Zero)
        {
            Marshal.FreeHGlobal(KeyPtr);
            KeyPtr = IntPtr.Zero;
        }
        if (ValuePtr != IntPtr.Zero)
        {
            Marshal.FreeHGlobal(ValuePtr);
            ValuePtr = IntPtr.Zero;
        }
        _client = null;
    }

    // Holding the client prevents it from being CG'd until all operations complete.
    private object? _client;


    public void OnCompleted(Action continuation)
    {
        _continuation = continuation;
        CheckRaceAndCallContinuation();
    }

    public bool IsCompleted => _completionState == COMPLETION_STAGE_CONTINUATION_EXECUTED;

    public T? GetResult() => _exception is null ? _result : throw _exception;
}
