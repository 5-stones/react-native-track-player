import { useEffect, useState } from 'react';
import { AppState, type AppStateStatus } from 'react-native';

export function useAppIsInBackground() {
  const [state, setState] = useState<AppStateStatus>(AppState.currentState);
  useEffect(() => AppState.addEventListener('change', setState).remove, []);
  return state === 'background';
}
