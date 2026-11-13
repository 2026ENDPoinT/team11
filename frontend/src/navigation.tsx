import { Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import {
  createStaticNavigation,
  type StaticParamList,
} from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import {
  createBottomTabNavigator,
  type BottomTabBarProps,
} from '@react-navigation/bottom-tabs';

import HomeIcon from './assets/icons/tab/home.svg';
import ScheduleIcon from './assets/icons/tab/schedule.svg';
import ReportIcon from './assets/icons/tab/report.svg';
import SettingsIcon from './assets/icons/tab/settings.svg';

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
import EventAddScreen from './screens/schedule/EventAddScreen';
import EventDayScreen from './screens/schedule/EventDayScreen';
import ReportScreen from './screens/report/ReportScreen';
import SleepRecordScreen from './screens/report/SleepRecordScreen';
import SettingsScreen from './screens/settings/SettingsScreen';
import ProfileScreen from './screens/settings/ProfileScreen';
import NotificationSettingsScreen from './screens/settings/NotificationSettingsScreen';
import ConsentManageScreen from './screens/settings/ConsentManageScreen';

// 피그마 'tab bar' (40:83): 높이 64, 패딩 8, 아이콘 20, 라벨 없음
const TAB_ICONS = {
  Home: { Icon: HomeIcon, label: '홈' },
  Schedule: { Icon: ScheduleIcon, label: '일정' },
  Report: { Icon: ReportIcon, label: '리포트' },
  Settings: { Icon: SettingsIcon, label: '설정' },
} as const;

function TabBar({ state, navigation }: BottomTabBarProps) {
  const { bottom } = useSafeAreaInsets();
  return (
    <View style={[styles.tabBar, { paddingBottom: 8 + bottom }]}>
      {state.routes.map((route, index) => {
        const { Icon, label } = TAB_ICONS[route.name as keyof typeof TAB_ICONS];
        const focused = state.index === index;
        return (
          <Pressable
            key={route.key}
            style={styles.tab}
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

// 일정 탭 안쪽 스택: 3-2-1 일정 확인은 피그마에서 탭 바가 보여서 탭 안에 둠
const ScheduleStack = createNativeStackNavigator({
  screenOptions: { headerShown: false },
  screens: {
    ScheduleHome: ScheduleScreen,
    EventDay: EventDayScreen,
  },
});

const MainTabs = createBottomTabNavigator({
  tabBar: props => <TabBar {...props} />,
  screenOptions: { headerShown: false },
  screens: {
    Home: HomeScreen,
    Schedule: ScheduleStack,
    Report: ReportScreen,
    Settings: SettingsScreen,
  },
});

// 헤더는 피그마 화면마다 직접 그리므로 기본 헤더는 끔
const RootStack = createNativeStackNavigator({
  initialRouteName: 'Splash',
  screenOptions: { headerShown: false },
  screens: {
    // 0 로그인 · 회원가입 · 온보딩
    Splash: SplashScreen,
    Login: LoginScreen,
    SignUp: SignUpScreen,
    Consent: ConsentScreen,
    SleepProfile: SleepProfileScreen,
    ExamProfile: ExamProfileScreen,
    NotificationPermission: NotificationPermissionScreen,
    TimetableImport: TimetableImportScreen,
    // 메인 탭
    Main: MainTabs,
    // 탭 위로 쌓이는 화면
    ClassEdit: ClassEditScreen,
    EventAdd: EventAddScreen,
    SleepRecord: SleepRecordScreen,
    Profile: ProfileScreen,
    NotificationSettings: NotificationSettingsScreen,
    ConsentManage: ConsentManageScreen,
  },
});

export const Navigation = createStaticNavigation(RootStack);

// navigation.navigate('화면이름') 에 자동완성/타입검사가 되게 하는 설정
type RootStackParamList = StaticParamList<typeof RootStack>;
declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}

const styles = StyleSheet.create({
  tabBar: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    padding: 8,
  },
  tab: {
    flex: 1,
    height: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
