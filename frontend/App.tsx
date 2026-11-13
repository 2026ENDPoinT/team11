import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { DefaultTheme } from '@react-navigation/native';
import { Navigation } from './src/navigation';

// 피그마 화면 배경이 흰색이라 React Navigation 기본 배경(회색)을 흰색으로 바꿈
const theme = {
  ...DefaultTheme,
  colors: { ...DefaultTheme.colors, background: '#FFFFFF' },
};

export default function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" />
      <Navigation theme={theme} />
    </SafeAreaProvider>
  );
}
