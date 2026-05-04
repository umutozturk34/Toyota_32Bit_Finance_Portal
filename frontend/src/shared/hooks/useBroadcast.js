import { useMutation, useQueryClient } from '@tanstack/react-query';
import { broadcastService } from '../services/broadcastService';

export function useSendBroadcast() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: broadcastService.send,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
}
