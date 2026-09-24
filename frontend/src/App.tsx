import { useState } from 'react';
import { ChatHeader } from './components/ChatHeader';
import { Composer } from './components/Composer';
import { ErrorBanner } from './components/ErrorBanner';
import { MessageList } from './components/MessageList';
import { useChat } from './hooks/useChat';
import { useElapsedSeconds } from './hooks/useElapsedSeconds';
import { loadConversationId, saveConversationId } from './utils/conversationId';

export function App() {
  const [conversationId, setConversationId] = useState(loadConversationId);
  const { connectionState, status, messages, streamingContent, errorMessage, sendMessage, sendContext, interrupt, reset } =
    useChat(conversationId);

  const hasStreamingContent = Boolean(streamingContent);
  const elapsedSeconds = useElapsedSeconds(status === 'PROCESSING');

  const handleChangeConversationId = (nextId: string) => {
    saveConversationId(nextId);
    setConversationId(nextId);
  };

  const handleReset = () => {
    if (window.confirm('Reiniciar esta conversa? O histórico será apagado no servidor.')) {
      reset();
    }
  };

  return (
    <div className="app-shell">
      <ChatHeader
        conversationId={conversationId}
        onChangeConversationId={handleChangeConversationId}
        connectionState={connectionState}
        status={status}
        hasStreamingContent={hasStreamingContent}
        elapsedSeconds={elapsedSeconds}
        onReset={handleReset}
      />

      <main className="chat-main">
        <MessageList messages={messages} streamingContent={streamingContent} status={status} elapsedSeconds={elapsedSeconds} />
      </main>

      <footer className="chat-footer">
        {errorMessage && <ErrorBanner message={errorMessage} />}
        <Composer
          status={status}
          connectionState={connectionState}
          onSendMessage={sendMessage}
          onSendContext={sendContext}
          onInterrupt={interrupt}
        />
      </footer>
    </div>
  );
}
