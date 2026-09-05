(() => {
  const DEPTH_ROW_COUNT = 20;

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
    feedRate: document.getElementById("feed-rate"),
    manageSourcesButton: document.getElementById("manage-sources-button"),
    sourcesDialog: document.getElementById("sources-dialog"),
    closeSourcesButton: document.getElementById("close-sources-button"),
    sourcesList: document.getElementById("sources-list"),
    imbalanceValue: document.getElementById("imbalance-value"),
    imbalanceDirection: document.getElementById("imbalance-direction"),
    imbalanceNeedle: document.getElementById("imbalance-needle"),
    imbalanceArc: document.getElementById("imbalance-arc"),
    tradeStrengthValue: document.getElementById("trade-strength-value"),
    tradeStrengthDirection: document.getElementById("trade-strength-direction"),
    tradeStrengthNeedle: document.getElementById("trade-strength-needle"),
    tradeStrengthArc: document.getElementById("trade-strength-arc")
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

  function safeQuantity(level) {
    const quantity = level && Number.isFinite(level.quantity) ? level.quantity : 0;
    return quantity > 0 ? quantity : 0;
  }

  function computeMaxQuantity(bidLevels, askLevels) {
    let max = 0;
    const bidCount = Math.min(DEPTH_ROW_COUNT, bidLevels.length);
    for (let i = 0; i < bidCount; i += 1) {
      max = Math.max(max, safeQuantity(bidLevels[i]));
    }
    const askCount = Math.min(DEPTH_ROW_COUNT, askLevels.length);
    for (let i = 0; i < askCount; i += 1) {
      max = Math.max(max, safeQuantity(askLevels[i]));
    }
    return max > 0 ? max : 1;
  }

  // Creates DEPTH_ROW_COUNT persistent DOM rows for one side of the ladder.
  // These nodes are created exactly once and mutated in place on every
  // subsequent render, so the DOM node count never grows over the app's
  // lifetime regardless of how many WebSocket frames arrive.
  function createDepthLadder(container, side) {
    const fragment = document.createDocumentFragment();
    const slots = [];

    for (let i = 0; i < DEPTH_ROW_COUNT; i += 1) {
      const row = document.createElement("div");
      row.className = `book-row ${side}-row is-empty`;

      const depth = document.createElement("div");
      depth.className = "depth";
      depth.style.width = "0%";

      const orders = document.createElement("span");
      orders.textContent = "—";

      const quantity = document.createElement("span");
      quantity.textContent = "—";

      const price = document.createElement("span");
      price.className = `${side}-price`;
      price.textContent = "—";

      row.append(depth);
      if (side === "bid") {
        row.append(orders, quantity, price);
      } else {
        row.append(price, quantity, orders);
      }

      fragment.appendChild(row);
      slots.push({ row, depth, orders, quantity, price });
    }

    container.replaceChildren(fragment);
    return slots;
  }

  // Mutates the fixed set of row slots in place. Never creates, appends,
  // or removes DOM nodes. Levels beyond the available data or beyond
  // DEPTH_ROW_COUNT are rendered as empty, zero-width placeholder rows.
  function updateDepthLadder(slots, levels, maxQuantity) {
    const availableCount = Array.isArray(levels) ? Math.min(levels.length, DEPTH_ROW_COUNT) : 0;

    for (let i = 0; i < DEPTH_ROW_COUNT; i += 1) {
      const slot = slots[i];

      if (i < availableCount) {
        const level = levels[i];
        const quantity = safeQuantity(level);
        const widthPercent = quantity > 0
          ? Math.min(100, Math.max(0, (quantity / maxQuantity) * 100))
          : 0;

        slot.depth.style.width = `${widthPercent}%`;
        slot.orders.textContent = formatInteger(level ? level.orders : undefined);
        slot.quantity.textContent = formatInteger(level ? level.quantity : undefined);
        slot.price.textContent = formatPrice(level ? level.price : undefined);
        slot.row.classList.remove("is-empty");
      } else {
        slot.depth.style.width = "0%";
        slot.orders.textContent = "—";
        slot.quantity.textContent = "—";
        slot.price.textContent = "—";
        slot.row.classList.add("is-empty");
      }
    }
  }

  const bidSlots = createDepthLadder(elements.bids, "bid");
  const askSlots = createDepthLadder(elements.asks, "ask");

  function clampPercent(value) {
    return Number.isFinite(value) ? Math.min(100, Math.max(-100, value)) : 0;
  }

  function gaugeDirection(value) {
    if (value > 0.25) return { label: "Buy pressure", cssClass: "positive" };
    if (value < -0.25) return { label: "Sell pressure", cssClass: "negative" };
    return { label: "Neutral", cssClass: "neutral" };
  }

  // Every SVG gauge is static markup created with the page. Rendering only
  // updates existing attributes/text, so incoming WebSocket frames never
  // allocate additional browser DOM nodes or retain chart history.
  function updateGauge(valueElement, directionElement, needleElement, arcElement, value) {
    const percent = clampPercent(value);
    const normalized = (percent + 100) / 200;
    const magnitude = Math.abs(percent) / 100;
    const angle = -180 + (normalized * 180);
    const halfArcLength = 141.375;
    const direction = gaugeDirection(percent);
    const arcPath = percent < 0
      ? "M 110 20 A 90 90 0 0 0 20 110"
      : "M 110 20 A 90 90 0 0 1 200 110";
    const angleRadians = angle * (Math.PI / 180);
    const needleTipX = 110 + (90 * Math.cos(angleRadians));
    const needleTipY = 110 + (90 * Math.sin(angleRadians));

    valueElement.textContent = `${percent >= 0 ? "+" : ""}${percent.toFixed(1)}%`;
    directionElement.textContent = direction.label;
    directionElement.className = `gauge-direction ${direction.cssClass}`;
    needleElement.setAttribute("x1", "110");
    needleElement.setAttribute("y1", "110");
    needleElement.setAttribute("x2", needleTipX.toFixed(3));
    needleElement.setAttribute("y2", needleTipY.toFixed(3));
    arcElement.setAttribute("d", arcPath);

    /*
     * The active arc starts at the neutral midpoint rather than at -100%.
     * A small imbalance therefore produces a short directional segment,
     * accurately communicating magnitude instead of a misleading half-full
     * gauge. Negative values fill leftward; positive values fill rightward.
     */
    arcElement.classList.toggle("gauge-negative", percent < 0);
    arcElement.classList.toggle("gauge-positive", percent > 0);
    arcElement.style.strokeDasharray = `${halfArcLength * magnitude} ${282.75}`;
    arcElement.style.strokeDashoffset = "0";

    needleElement.parentElement.parentElement.parentElement.setAttribute(
      "aria-valuenow",
      percent.toFixed(1)
    );
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
    elements.priceChange.textContent = previousPrice === null
      ? "First live price"
      : `${change >= 0 ? "+" : ""}${formatPrice(change)} since prior tick`;
    elements.priceChange.className = `change ${
      change > 0 ? "positive" : change < 0 ? "negative" : "neutral"
    }`;
    previousPrice = snapshot.lastPrice;

    const bidLevels = Array.isArray(snapshot.bids) ? snapshot.bids : [];
    const askLevels = Array.isArray(snapshot.asks) ? snapshot.asks : [];
    const maxQuantity = computeMaxQuantity(bidLevels, askLevels);

    updateDepthLadder(bidSlots, bidLevels, maxQuantity);
    updateDepthLadder(askSlots, askLevels, maxQuantity);

    updateGauge(
      elements.imbalanceValue,
      elements.imbalanceDirection,
      elements.imbalanceNeedle,
      elements.imbalanceArc,
      snapshot.depthImbalancePercent
    );
    updateGauge(
      elements.tradeStrengthValue,
      elements.tradeStrengthDirection,
      elements.tradeStrengthNeedle,
      elements.tradeStrengthArc,
      snapshot.tradeStrengthPercent
    );

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

  function sourceStatusLabel(status) {
    return status.replaceAll("_", " ").toLowerCase()
      .replace(/\b\w/g, letter => letter.toUpperCase());
  }

  function renderSources(sources) {
    elements.sourcesList.replaceChildren(...sources.map(source => {
      const card = document.createElement("article");
      card.className = `source-card${source.selected ? " active-source" : ""}`;

      const header = document.createElement("div");
      header.className = "source-card-header";

      const title = document.createElement("div");
      const name = document.createElement("h3");
      name.className = "source-name";
      name.textContent = source.displayName;

      const code = document.createElement("p");
      code.className = "source-code";
      code.textContent = source.source;

      title.append(name, code);

      const status = document.createElement("span");
      status.className = `source-status status-${source.status.toLowerCase().replaceAll("_", "-")}`;
      status.textContent = sourceStatusLabel(source.status);

      header.append(title, status);

      const detail = document.createElement("p");
      detail.className = "source-detail";
      detail.textContent = source.source === "MOCK"
        ? "Synthetic local feed for dashboard development and pipeline verification."
        : source.credentialsConfigured
          ? "Credentials were detected on this server. The transport adapter is not implemented yet."
          : "Credentials have not been detected. Use the server-local setup instructions below.";

      const actions = document.createElement("div");
      actions.className = "source-actions";

      if (source.source === "MOCK") {
        const active = document.createElement("span");
        active.className = "source-detail";
        active.textContent = source.selected ? "Active dashboard source" : "Available dashboard source";
        actions.append(active);
      } else {
        const copyButton = document.createElement("button");
        copyButton.className = "source-action";
        copyButton.type = "button";
        copyButton.textContent = "Copy setup instructions";
        copyButton.addEventListener("click", async () => {
          try {
            await navigator.clipboard.writeText(source.setupInstructions);
            copyButton.textContent = "Copied";
            window.setTimeout(() => { copyButton.textContent = "Copy setup instructions"; }, 1400);
          } catch {
            copyButton.textContent = "Copy unavailable";
          }
        });

        const pending = document.createElement("span");
        pending.className = "source-detail";
        pending.textContent = "Selection becomes available after adapter implementation.";
        actions.append(copyButton, pending);
      }

      const instructions = document.createElement("pre");
      instructions.className = "setup-instructions";
      instructions.textContent = source.setupInstructions;

      card.append(header, detail, actions, instructions);
      return card;
    }));
  }

  async function loadSources() {
    elements.sourcesList.replaceChildren(Object.assign(document.createElement("p"), {
      className: "sources-loading",
      textContent: "Loading source status…"
    }));

    try {
      const response = await fetch("/api/sources", { headers: { Accept: "application/json" } });
      if (!response.ok) {
        throw new Error(`Source status request failed with ${response.status}`);
      }
      renderSources(await response.json());
    } catch (error) {
      elements.sourcesList.replaceChildren(Object.assign(document.createElement("p"), {
        className: "sources-loading",
        textContent: "Unable to load source status. The dashboard data stream remains unaffected."
      }));
      console.error("Unable to load source status", error);
    }
  }

  function openSources() {
    if (typeof elements.sourcesDialog.showModal === "function") {
      elements.sourcesDialog.showModal();
    } else {
      elements.sourcesDialog.setAttribute("open", "");
    }
    loadSources();
  }

  elements.manageSourcesButton.addEventListener("click", openSources);
  elements.closeSourcesButton.addEventListener("click", () => elements.sourcesDialog.close());
  elements.sourcesDialog.addEventListener("click", event => {
    if (event.target === elements.sourcesDialog) {
      elements.sourcesDialog.close();
    }
  });

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
