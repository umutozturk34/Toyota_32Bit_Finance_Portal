import { useQuery } from '@tanstack/react-query';

export default function useMarketListData(marketKey, fetcher, params) {
  return useQuery({
    queryKey: [marketKey, params],
    queryFn: () => fetcher(params),
    placeholderData: (prev) => prev,
  });
}
