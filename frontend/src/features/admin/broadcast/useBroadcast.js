import { useMutation } from '@tanstack/react-query';
import api from '../../../shared/services/api';

const ENDPOINT = '/admin/notifications/broadcast';

async function postBroadcast({ title, body }) {
  const response = await api.post(ENDPOINT, { title, body });
  return response.data.data;
}

export function useBroadcast() {
  return useMutation({ mutationFn: postBroadcast });
}
