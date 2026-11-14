import { Platform } from 'react-native';
import {
  isHealthDataAvailableAsync,
  requestAuthorization,
  queryCategorySamples,
  CategoryValueSleepAnalysis,
} from '@kingstinct/react-native-healthkit';
import * as api from './api/endpoints';
import { kstIsoFromDate } from './kst';

export const isHealthKitSupported = Platform.OS === 'ios';

const SLEEP_TYPE = 'HKCategoryTypeIdentifierSleepAnalysis';
const LOOKBACK_DAYS = 30;
// ponytail: 1시간 이내 간격은 같은 밤으로 합친다(수면 단계가 여러 샘플로 쪼개져 들어옴). 낮잠이 바로 붙으면 오합산될 수 있음 — 체감상 문제되면 간격 줄이기.
const MERGE_GAP_MS = 60 * 60 * 1000;

/** 건강 앱(HealthKit) 수면 분석을 읽어 밤 단위로 합친 뒤 백엔드로 보낸다. */
export async function importSleepFromHealthKit() {
  if (!isHealthKitSupported) throw new Error('건강 앱 연동은 iOS에서만 지원돼요.');
  if (!(await isHealthDataAvailableAsync())) throw new Error('이 기기에서는 건강 데이터를 사용할 수 없어요.');

  const granted = await requestAuthorization({ toRead: [SLEEP_TYPE] });
  if (!granted) throw new Error('건강 앱 접근 권한이 필요해요.');

  const since = new Date(Date.now() - LOOKBACK_DAYS * 24 * 60 * 60 * 1000);
  const samples = await queryCategorySamples(SLEEP_TYPE, {
    limit: 0,
    ascending: true,
    filter: { date: { startDate: since } },
  });

  const nights = mergeNights(samples);
  if (nights.length === 0) return { imported: 0, skipped: 0, invalid: [] };
  return api.importHealthKitSleep(nights);
}

function mergeNights(samples: readonly { startDate: Date; endDate: Date; value: CategoryValueSleepAnalysis }[]) {
  const asleep = [...samples]
    .filter(s => s.value !== CategoryValueSleepAnalysis.awake)
    .sort((a, b) => a.startDate.getTime() - b.startDate.getTime());

  type Night = { bedAt: string; wakeAt: string; wakeAtMs: number };
  const nights: Night[] = [];
  for (const s of asleep) {
    const last = nights[nights.length - 1];
    if (last && s.startDate.getTime() - last.wakeAtMs <= MERGE_GAP_MS) {
      if (s.endDate.getTime() > last.wakeAtMs) {
        last.wakeAt = kstIsoFromDate(s.endDate);
        last.wakeAtMs = s.endDate.getTime();
      }
    } else {
      nights.push({ bedAt: kstIsoFromDate(s.startDate), wakeAt: kstIsoFromDate(s.endDate), wakeAtMs: s.endDate.getTime() });
    }
  }
  return nights.slice(-100).map(({ bedAt, wakeAt }) => ({ bedAt, wakeAt })); // /sleep-logs/import는 요청당 최대 100개
}
