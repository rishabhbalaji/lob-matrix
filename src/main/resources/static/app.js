(() => {
  const elements = {
    indicator: document.getElementById("connection-indicator"),
    connectionStatus: document.getElementById("connection-status"),
    symbol: document.getElementById("symbol"),
    source: document.getElementById("source"),
    token: document.getElementById("token"),
    lastUpdate: document.getElementById("last-update"),
    lastPrice: document.getElementById("last-price"),
    priceChange: document.getElementById("price-change"),
    ltq: document.getElementById("ltq"),
    volume: document.getElementById("volume"),
    midPrice: document.getElementById("mid-price"),
    spread: document.getElementById("spread"),
    bookState: document.getElementById("book-state"),
    bids: document.getElementById("bids"),
    asks: document.getElementById("asks"),
    feedRate: document.getElementById("feed-rate")
  };

  let latestSnapshot = null;
  let renderQueued = false;
  let reconnectAttempt = 0;
  let previousPrice = null;
  let recentTicks = [];

  const formatter = new Intl.NumberFormat(undefined, {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  const integerFormatter = new Intl.NumberFormat();

  function setConnection(status, cssClass) {
    elements.connectionStatus.textContent = status;
    elements.indicator.className = `status-dot ${cssClass}`;
  }

  function formatPrice(value) {
    return Number.isFinite(value) ? formatter.format(value) : "—";
  }

  function formatInteger(value) {
    return Number.isFinite(value) ? integerFormatter.format(value) : "—";
  }

  function renderRows(container, levels, side) {
    const maxQuantity = Math.max(1, ...levels.map(level => level.quantity));
    container.replaceChildren(...levels.map(level => {
      const row = document.createElement("div");
      row.className = `book-row ${side}-row`;

      const depth = document.createElement("div");
      depth.className = "depth";
      depth.style.width = `${Math.max(4, (level.quantity / maxQuantity) * 100)}%`;

      const orders = document.createElement("span");
      orders.textContent = formatInteger(level.orders);

      const quantity = document.createElement("span");
      quantity.textContent = formatInteger(level.quantity);

      const price = document.createElement("span");
      price.className = `${side}-price`;
      price.textContent = formatPrice(level.price);

      row.append(depth);
      if (side === "bid") {
        row.append(orders, quantity, price);
      } else {
        row.append(price, quantity, orders);
      }
      return row;
    }));
  }

  function render() {
    renderQueued = false;
    const snapshot = latestSnapshot;
    if (!snapshot) return;

    elements.symbol.textContent = snapshot.symbol;
    elements.source.textContent = snapshot.source;
    elements.token.textContent = snapshot.token;
    elements.lastPrice.textContent = formatPrice(snapshot.lastPrice);
    elements.ltq.textContent = formatInteger(snapshot.lastTradedQuantity);
    elements.volume.textContent = formatInteger(snapshot.volume);
    elements.midPrice.textContent = formatPrice(snapshot.midPrice);
    elements.spread.textContent = formatPrice(snapshot.spread);
    elements.bookState.textContent = snapshot.bookState;

    const timestamp = new Date(snapshot.timestampMicros / 1000);
    elements.lastUpdate.textContent = Number.isNaN(timestamp.getTime())
      ? "—"
      : timestamp.toLocaleTimeString();

    const change = previousPrice === null ? 0 : snapshot.lastPrice - previousPrice;
    const changeText = previousPrice === null
      ? "First live price"
      : `${change >= 0 ? "+" : ""}${formatPrice(change)} since prior tick`;

    elements.priceChange.textContent = changeText;
    elements.priceChange.className = `change ${
      change > 0 ? "positive" : change < 0 ? "negative" : "neutral"
    }`;
    previousPrice = snapshot.lastPrice;

    renderRows(elements.bids, snapshot.bids, "bid");
    renderRows(elements.asks, snapshot.asks, "ask");

    const now = performance.now();
    recentTicks.push(now);
    recentTicks = recentTicks.filter(time => now - time <= 1000);
    elements.feedRate.textContent = `${recentTicks.length} updates/sec rendered`;
  }

  function scheduleRender(snapshot) {
    latestSnapshot = snapshot;
    if (!renderQueued) {
      renderQueued = true;
      window.requestAnimationFrame(render);
    }
  }

  function connect() {
    const scheme = window.location.protocol === "https:" ? "wss" : "ws";
    const socket = new WebSocket(`${scheme}://${window.location.host}/ws/orderbook`);

    setConnection("Connecting", "status-connecting");

    socket.onopen = () => {
      reconnectAttempt = 0;
      setConnection("Live", "status-live");
    };

    socket.onmessage = event => {
      try {
        const snapshot = JSON.parse(event.data);
        if (snapshot.type === "orderbook_snapshot") {
          scheduleRender(snapshot);
        }
      } catch (error) {
        console.error("Unable to parse order book update", error);
      }
    };

    socket.onerror = () => {
      socket.close();
    };

    socket.onclose = () => {
      const delay = Math.min(10000, 500 * (2 ** reconnectAttempt));
      reconnectAttempt += 1;
      setConnection(`Reconnecting in ${(delay / 1000).toFixed(1)}s`, "status-offline");
      window.setTimeout(connect, delay);
    };
  }

  connect();
})();
