// Copyright Valkey GLIDE Project Contributors - SPDX Identifier: Apache-2.0

using System.Collections.Concurrent;

namespace Glide;


internal class MessageContainer<T>
{
    internal Message<T> GetMessage(int index) => _messages[index];

    internal Message<T> GetMessageForCall(IntPtr[] args, int argsCount)
    {
        Message<T> message = GetFreeMessage();
        message.SetupTask(args, argsCount, this);
        return message;
    }

    private Message<T> GetFreeMessage()
    {
        if (!_availableMessages.TryDequeue(out Message<T>? message))
        {
            lock (_messages)
            {
                int index = _messages.Count;
                message = new Message<T>(index, this);
                _messages.Add(message);
            }
        }
        return message;
    }

    public void ReturnFreeMessage(Message<T> message) => _availableMessages.Enqueue(message);

    internal void DisposeWithError(Exception? error)
    {
        lock (_messages)
        {
            foreach (Message<T>? message in _messages.Where(message => !message.IsCompleted))
            {
                try
                {
                    message.SetException(new TaskCanceledException("Client closed", error));
                }
                catch (Exception) { }
            }
            _messages.Clear();
        }
        _availableMessages.Clear();
    }

    /// This list allows us random-access to the message in each index,
    /// which means that once we receive a callback with an index, we can
    /// find the message to resolve in constant time.
    private readonly List<Message<T>> _messages = [];

    /// This queue contains the messages that were created and are currently unused by any task,
    /// so they can be reused y new tasks instead of allocating new messages.
    private readonly ConcurrentQueue<Message<T>> _availableMessages = new();
}
