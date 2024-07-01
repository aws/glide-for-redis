// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

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
    /// The array holding the pointers  to the unmanaged memory that contains the operation's arguments.
    public IntPtr[]? Args { get; private set; }
    // We need to save the args count, because sometimes we get arrays that are larger than they need to be. We can't rely on `this.args.Length`, due to it coming from an array pool.
    private int _argsCount;
    private MessageContainer<T> Container { get; } = container;
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
                Container.ReturnFreeMessage(this);
            }
        }
    }

    public Message<T> GetAwaiter() => this;

    /// This returns a task that will complete once SetException / SetResult are called,
    /// and ensures that the internal state of the message is set-up before the task is created,
    /// and cleaned once it is complete.
    public void SetupTask(IntPtr[] arguments, int argsCount, object client)
    {
        _continuation = null;
        _completionState = COMPLETION_STAGE_STARTED;
        _result = default;
        _exception = null;
        _client = client;
        Args = arguments;
        _argsCount = argsCount;
    }

    // This function isn't thread-safe. Access to it should be from a single thread, and only once per operation.
    // For the sake of performance, this responsibility is on the caller, and the function doesn't contain any safety measures.
    private void FreePointers()
    {
        if (Args is { })
        {
            for (int i = 0; i < _argsCount; i++)
            {
                Marshal.FreeHGlobal(Args[i]);
            }
            Args = null;
            _argsCount = 0;
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
