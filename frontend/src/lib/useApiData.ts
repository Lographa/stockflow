"use client";

import { useEffect, useRef, useState } from "react";

interface Result<T> {
  /** Identidade do recurso (ex.: id do produto + filtros). */
  key: string;
  /** Identidade + contador de recarga: identifica exatamente a requisição. */
  requestId: string;
  data?: T;
  error?: Error;
}

export interface ApiData<T> {
  /**
   * Último dado carregado para a mesma `key`. É mantido durante uma recarga
   * (`reload`) para a tela não "piscar"; é `undefined` ao trocar de `key`
   * ou após uma falha.
   */
  data: T | undefined;
  /** Erro da última requisição concluída (limpo enquanto recarrega). */
  error: Error | undefined;
  /** `true` enquanto a requisição atual não terminou. */
  loading: boolean;
  /** Refaz a requisição (mantém `data` anterior até a resposta chegar). */
  reload: () => void;
}

/**
 * Carrega um recurso da API com cancelamento (AbortController), estados de
 * carregando/erro e recarga manual. `key` identifica o recurso: mude-a para
 * disparar nova requisição. "Carregando" é derivado da comparação entre a
 * requisição atual e a do último resultado (sem setState síncrono no efeito).
 */
export function useApiData<T>(
  load: (signal: AbortSignal) => Promise<T>,
  key: string,
): ApiData<T> {
  const loadRef = useRef(load);
  useEffect(() => {
    loadRef.current = load;
  });

  const [reloadCount, setReloadCount] = useState(0);
  const [result, setResult] = useState<Result<T> | null>(null);
  const requestId = `${key}|${reloadCount}`;

  useEffect(() => {
    const controller = new AbortController();
    loadRef
      .current(controller.signal)
      .then((data) => {
        if (!controller.signal.aborted) {
          setResult({ key, requestId, data });
        }
      })
      .catch((err: unknown) => {
        if (controller.signal.aborted) return;
        setResult({
          key,
          requestId,
          error: err instanceof Error ? err : new Error(String(err)),
        });
      });
    return () => controller.abort();
  }, [key, requestId]);

  const loading = result?.requestId !== requestId;
  const sameResource = result?.key === key;

  return {
    data: sameResource ? result?.data : undefined,
    error: !loading && sameResource ? result?.error : undefined,
    loading,
    reload: () => setReloadCount((count) => count + 1),
  };
}
