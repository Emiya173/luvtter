-- 中文 bigram 全文搜索:
-- 1) letter_bigram(text)  → 把 ASCII 词转小写, CJK/非ASCII 字符同时输出单字与相邻 bigram
-- 2) letter_bigram_query(text) → 把用户查询转成 AND 形式的 tsquery
-- 3) letter_contents 上的 BEFORE INSERT/UPDATE 触发器自动维护 index_tsv

CREATE OR REPLACE FUNCTION letter_bigram(input TEXT) RETURNS TEXT AS $$
DECLARE
    out_text TEXT := '';
    cur_word TEXT := '';
    ch TEXT;
    next_ch TEXT;
    i INT := 1;
    n INT;
BEGIN
    IF input IS NULL THEN
        RETURN '';
    END IF;
    n := char_length(input);
    WHILE i <= n LOOP
        ch := substr(input, i, 1);
        IF ch ~ '[A-Za-z0-9]' THEN
            cur_word := cur_word || lower(ch);
            i := i + 1;
        ELSIF ch ~ '\s' OR ch ~ '[[:punct:]]' THEN
            IF length(cur_word) > 0 THEN
                out_text := out_text || ' ' || cur_word;
                cur_word := '';
            END IF;
            i := i + 1;
        ELSE
            -- 非ASCII (CJK 等): 输出该字, 若下一个也是非ASCII/punct/space 则同时输出 bigram
            IF length(cur_word) > 0 THEN
                out_text := out_text || ' ' || cur_word;
                cur_word := '';
            END IF;
            out_text := out_text || ' ' || ch;
            IF i < n THEN
                next_ch := substr(input, i + 1, 1);
                IF next_ch !~ '[A-Za-z0-9]' AND next_ch !~ '\s' AND next_ch !~ '[[:punct:]]' THEN
                    out_text := out_text || ' ' || ch || next_ch;
                END IF;
            END IF;
            i := i + 1;
        END IF;
    END LOOP;
    IF length(cur_word) > 0 THEN
        out_text := out_text || ' ' || cur_word;
    END IF;
    RETURN trim(out_text);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION letter_bigram_query(input TEXT) RETURNS tsquery AS $$
DECLARE
    tokens TEXT;
BEGIN
    tokens := letter_bigram(input);
    IF tokens IS NULL OR length(tokens) = 0 THEN
        RETURN NULL;
    END IF;
    RETURN to_tsquery('simple', regexp_replace(tokens, '\s+', ' & ', 'g'));
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION letter_contents_tsv_update() RETURNS TRIGGER AS $$
BEGIN
    NEW.index_tsv := to_tsvector('simple', letter_bigram(coalesce(NEW.index_text, '')));
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS letter_contents_tsv_trigger ON letter_contents;
CREATE TRIGGER letter_contents_tsv_trigger
BEFORE INSERT OR UPDATE OF index_text ON letter_contents
FOR EACH ROW EXECUTE FUNCTION letter_contents_tsv_update();

-- 回填已有行
UPDATE letter_contents SET index_text = index_text WHERE index_text IS NOT NULL;
