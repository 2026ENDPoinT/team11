import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { DefaultTheme } from '@react-navigation/native';
import { Navigation } from './src/navigation';
import { SessionProvider } from './src/session';
import { C } from './src/theme';

const theme = { ...DefaultTheme, colors: { ...DefaultTheme.colors, background: C.bg } };

export default function App() {
  return (
    <SafeAreaProvider>
      <SessionProvider>
        <StatusBar barStyle="dark-content" />
        <Navigation theme={theme} />
      </SessionProvider>
    </SafeAreaProvider>
  );
}
