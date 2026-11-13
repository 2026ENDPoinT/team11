import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { createStaticNavigation, type StaticParamList } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator, type BottomTabBarProps } from '@react-navigation/bottom-tabs';

import HomeIcon from './assets/icons/tab-home.svg';
import ScheduleIcon from './assets/icons/tab-schedule.svg';
import ReportIcon from './assets/icons/tab-report.svg';
import SettingsIcon from './assets/icons/tab-settings.svg';

import SplashScreen from './screens/auth/SplashScreen';
import LoginScreen from './screens/auth/LoginScreen';
import SignUpScreen from './screens/auth/SignUpScreen';
import ConsentScreen from './screens/auth/ConsentScreen';
import SleepProfileScreen, { ExamProfileScreen } from './screens/auth/SleepProfileScreen';
import NotificationPermissionScreen from './screens/auth/NotificationPermissionScreen';
import TimetableImportScreen from './screens/auth/TimetableImportScreen';
import HomeScreen from './screens/home/HomeScreen';
import ScheduleScreen from './screens/schedule/ScheduleScreen';
import ClassEditScreen from './screens/schedule/ClassEditScreen';
import EventEditScreen from './screens/schedule/EventEditScreen';
import ReportScreen from './screens/report/ReportScreen';
import SleepRecordScreen from './screens/report/SleepRecordScreen';
import SettingsScreen from './screens/settings/SettingsScreen';
import ProfileScreen from './screens/settings/ProfileScreen';
import PasswordChangeScreen from './screens/settings/PasswordChangeScreen';
import NotificationSettingsScreen from './screens/settings/NotificationSettingsScreen';
import ConsentManageScreen from './screens/settings/ConsentManageScreen';
import { C } from './theme';

// tab bar(257:397): 높이 64, 패딩 8, 아이콘 20, 라벨 없음. 활성 #333 / 비활성 #9C9C9C
const TABS = {
  Home: { Icon: HomeIcon, label: '홈' },
  Schedule: { Icon: ScheduleIcon, label: '일정' },
  Report: { Icon: ReportIcon, label: '리포트' },
  Settings: { Icon: SettingsIcon, label: '설정' },
} as const;

function TabBar({ state, navigation }: BottomTabBarProps) {
  const { bottom } = useSafeAreaInsets();
  return (
    <View style={[st.tabBar, { paddingBottom: 8 + bottom }]}>
      {state.routes.map((route, i) => {
        const { Icon, label } = TABS[route.name as keyof typeof TABS];
        const focused = state.index === i;
        return (
          <Pressable
            key={route.key}
            style={st.tab}
            accessibilityRole="tab"
            accessibilityLabel={label}
            accessibilityState={{ selected: focused }}
            onPress={() => navigation.navigate(route.name)}
          >
            <Icon width={20} height={20} color={focused ? '#333333' : '#9C9C9C'} />
          </Pressable>
        );
      })}
    </View>
  );
}

const MainTabs = createBottomTabNavigator({
  tabBar: props => <TabBar {...props} />,
  screenOptions: { headerShown: false },
  screens: { Home: HomeScreen, Schedule: ScheduleScreen, Report: ReportScreen, Settings: SettingsScreen },
});

// 헤더는 화면마다 직접 그린다(header 컴포넌트)
const RootStack = createNativeStackNavigator({
  initialRouteName: 'Splash',
  screenOptions: { headerShown: false, contentStyle: { backgroundColor: C.bg } },
  screens: {
    Splash: SplashScreen,
    Login: LoginScreen,
    SignUp: SignUpScreen,
    Consent: ConsentScreen,
    SleepProfile: SleepProfileScreen,
    ExamProfile: ExamProfileScreen,
    NotificationPermission: NotificationPermissionScreen,
    TimetableImport: TimetableImportScreen,
    Main: MainTabs,
    // 탭 위로 쌓이는 화면
    ClassEdit: ClassEditScreen,
    EventEdit: EventEditScreen,
    SleepRecord: SleepRecordScreen,
    Profile: ProfileScreen,
    PasswordChange: PasswordChangeScreen,
    NotificationSettings: NotificationSettingsScreen,
    ConsentManage: ConsentManageScreen,
  },
});

export const Navigation = createStaticNavigation(RootStack);

type RootStackParamList = StaticParamList<typeof RootStack>;
declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}

const st = StyleSheet.create({
  tabBar: { flexDirection: 'row', backgroundColor: C.bg, padding: 8 },
  tab: { flex: 1, height: 48, alignItems: 'center', justifyContent: 'center' },
});
