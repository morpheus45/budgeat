-- Budgeat — comptes et plans
--
-- Tout le monde entre immédiatement : l'inscription crée un profil en plan
-- « launch », qui donne l'accès complet. Le jour où un plan payant arrive, il
-- suffit de basculer des lignes vers 'pro' et de restreindre 'free' — aucune
-- migration de structure.
--
-- Le point important : le plan vit ICI, pas sur le téléphone. Un client peut
-- toujours mentir sur ce qu'il est ; c'est la base qui tranche.

create table if not exists public.profiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  email       text,
  prenom      text,
  -- 'launch' : accès complet offert pendant le lancement
  -- 'free'   : palier gratuit restreint, le jour où le payant existera
  -- 'pro'    : palier payant
  plan        text not null default 'launch'
              check (plan in ('launch', 'free', 'pro')),
  -- Renseigné seulement pour 'pro'. Null = pas d'échéance.
  plan_expire timestamptz,
  cree_le     timestamptz not null default now()
);

alter table public.profiles enable row level security;

-- Chacun ne voit et ne modifie que son propre profil.
drop policy if exists "profil lisible par son proprietaire" on public.profiles;
create policy "profil lisible par son proprietaire"
  on public.profiles for select
  using (auth.uid() = id);

drop policy if exists "profil modifiable par son proprietaire" on public.profiles;
create policy "profil modifiable par son proprietaire"
  on public.profiles for update
  using (auth.uid() = id)
  with check (auth.uid() = id);

-- Volontairement AUCUNE policy d'insert ni d'update sur `plan` côté client :
-- le profil est créé par le trigger ci-dessous, et seul le service_role
-- (donc un serveur, jamais l'app) pourra faire passer quelqu'un en 'pro'.
-- Sans ça, n'importe qui s'offrirait l'abonnement depuis son téléphone.
revoke update (plan, plan_expire) on public.profiles from authenticated;

-- Création automatique du profil à l'inscription.
create or replace function public.creer_profil()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, prenom)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'prenom', '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.creer_profil();
